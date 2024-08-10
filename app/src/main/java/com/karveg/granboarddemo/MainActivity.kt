package com.karveg.granboarddemo

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.karveg.granboarddemo.models.DartData
import com.karveg.granboarddemo.models.LedData
import com.karveg.granboarddemo.store.DataStore
import com.lb.vector_child_finder_library.VectorChildFinder
import java.util.UUID
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var scanning: Boolean = false
    private lateinit var scanResults: MutableList<BluetoothDevice>
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    private val deviceList: MutableList<String> = mutableListOf()
    private var deviceConnected: BluetoothDevice? = null
    private var gattDeviceDiana: BluetoothGatt? = null
    private var connected: Boolean = false

    private lateinit var buttonScan: Button
    private lateinit var buttonConnect: Button
    private lateinit var listView: ListView
    private lateinit var editText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the ActivityResultLauncher for requesting permissions
        requestPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions: Map<String, Boolean> ->
            handlePermissionsResult(permissions)
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        // Get BluetoothAdapter from BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no está soportado", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Verificar si BLE está soportado
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE no está soportado", Toast.LENGTH_SHORT).show()
            finish()
        }

        bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
        scanResults = mutableListOf()

        eventsUI()
    }

    override fun onResume() {
        super.onResume()
        // Verificar y solicitar permisos en onResume
        if (!hasPermissions()) {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun eventsUI() {

        buttonScan = findViewById(R.id.scan_button)
        buttonConnect = findViewById(R.id.stateConnection)
        listView = findViewById(R.id.devices_list)
        editText = findViewById(R.id.text)

        buttonScan.setOnClickListener {
            if (!scanning) startScan() else stopScan()
        }

        // Configurar ListView
        deviceListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        listView.adapter = deviceListAdapter

        // Configurar el listener para clic largo
        listView.setOnItemClickListener { _, _, position, _ ->
            // Obtén el elemento pulsado
            val item = deviceList[position].split("·")[1].trim()
            Log.e("GattCallback", item)

            deviceConnected = scanResults.find { it.address.lowercase() == item.lowercase() }
            if (deviceConnected != null)
                gattDeviceDiana = deviceConnected!!.connectGatt(this, false, gattCallback)

        }

        buttonConnect.setOnClickListener {


            // Obtener la referencia al ImageView
            val imageView = findViewById<ImageView>(R.id.imageView)


            // Obtener el VectorDrawable
            val vectorDrawable = ContextCompat.getDrawable(this, R.drawable.dartboard)

            imageView.setImageDrawable(vectorDrawable)
            val vector: VectorChildFinder = VectorChildFinder(this, R.drawable.dartboard, imageView)

            val dataDart: DartData? = DataStore.dartDataList.find { it.value == editText.text.toString() }

            if(dataDart != null){

                val x = vector.findPathByName(dataDart.pathBoard)
                if (x != null) x.setFillColor(Color.BLUE)

                val l = vector.findPathByName(dataDart.pathLbl)
                if (l != null) l.setFillColor(Color.YELLOW)

                imageView.invalidate();

            }




            /*   if (connected) {
                   //Desconectarse
               } else {

               }

             */
        }

    }

    //region Permisos
    private fun hasPermissions(): Boolean {
        return (checkSelfPermission(Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    private fun handlePermissionsResult(permissions: Map<String, Boolean>) {
        val allPermissionsGranted = permissions.values.all { it }
        if (allPermissionsGranted) {
            // All permissions granted, proceed with desired functionality
            Toast.makeText(this, "Todos los permisos concedidos.", Toast.LENGTH_SHORT).show()
        } else {
            // Some permissions were denied, handle accordingly
            Toast.makeText(
                this,
                "Todos los permisos son necesarios para la funcionalidad completa.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    //endregion

    //region Scan
    @SuppressLint("MissingPermission")
    private fun startScan() {
        val scanPerior: Long = 10000 // Escanea por 10 segundos
        // Detener el escaneo después de un período definido
        Handler(Looper.getMainLooper()).postDelayed({ stopScan() }, scanPerior)
        deviceList.clear()
        scanning = true
        bluetoothLeScanner.startScan(leScanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        scanning = false
        bluetoothLeScanner.stopScan(leScanCallback)
    }

    @SuppressLint("MissingPermission")
    private val leScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val rssi = abs(result.rssi)// Obtén el RSSI en dBm
            if (!scanResults.contains(device) && result.rssi < 100) {
                scanResults.add(device)
                val deviceName = (device.name ?: "Unknown Device").trim()
                val deviceAddress = device.address.trim()
                if (deviceName != "Unknown Device") deviceList.add("$deviceName · $deviceAddress · $rssi")
                runOnUiThread {
                    deviceListAdapter.notifyDataSetChanged()
                }
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            Toast.makeText(this@MainActivity, "ERdddddROR", Toast.LENGTH_SHORT).show()
            // No es necesario para escaneo simple, pero se puede usar para resultados por lotes
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("ScanCallback", "Error en el escaneo: $errorCode")
            Toast.makeText(this@MainActivity, "ERROR", Toast.LENGTH_SHORT).show()
        }
    }
    //endregion

    //region BLE - Gatt
    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("GattCallback", "Conectado al GATT server.")
                runOnUiThread { buttonConnect.text = "Connected" }
                connected = true;
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("GattCallback", "Desconectado del GATT server.")
                runOnUiThread { buttonConnect.text = "Disconnected" }
                connected = false;
                gatt?.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Reemplaza con los UUIDs de tu servicio y característica específicos
                val serviceUUID = UUID.fromString("442f1570-8a00-9a28-cbe1-e1d4212d53eb")
                val characteristicUUID = UUID.fromString("442f1571-8a00-9a28-cbe1-e1d4212d53eb")

                // Aquí puedes acceder a los servicios y características
                val service = gatt?.getService(serviceUUID)
                val characteristic: BluetoothGattCharacteristic? =
                    service?.getCharacteristic(characteristicUUID)

                if (characteristic != null) {

                    gatt.readCharacteristic(characteristic)

                    // Subscribirse a notificaciones
                    gatt.setCharacteristicNotification(characteristic, true)

                    // Configurar el Client Characteristic Configuration Descriptor (CCCD)
                    val descriptor =
                        characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    descriptor.let {
                        // Leer el descriptor
                        it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(it)
                    }

                    Log.w("GattCallback", "Connectado 100%")
                } else {
                    Log.w("GattCallback", "Característica no encontrada")
                }


            }
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java - API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            characteristic?.let {
                val data: ByteArray = it.value
                Log.i("GattCallback", "Notificación recibida: ${data}")

            }
        }
    }
    //endregion

}