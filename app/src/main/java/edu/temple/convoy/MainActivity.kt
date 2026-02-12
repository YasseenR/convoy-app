package edu.temple.convoy
import android.view.View
import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.TextView
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.CameraUpdateFactory
import kotlinx.coroutines.launch
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import edu.temple.convoy.ApiClient
import androidx.appcompat.app.AlertDialog


class MainActivity : AppCompatActivity() {


    private lateinit var store: SessionStore
    private var gMap: GoogleMap? = null
    private var userMarker: Marker? = null
    private val LOCATION_REQ = 1001
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var activeConvoyId: String? = null

    private fun setConvoyUI(convoyId: String?, tv: TextView, btnStart: Button, btnEnd: Button) {
        if (!convoyId.isNullOrBlank()) {
            activeConvoyId = convoyId
            tv.text = "Convoy: $convoyId"
            btnStart.visibility = View.GONE
            btnEnd.visibility = View.VISIBLE
        } else {
            activeConvoyId = null
            tv.text = "No convoy"
            btnEnd.visibility = View.GONE
            btnStart.visibility = View.VISIBLE
        }
    }

    private fun startConvoyService() {
        val svc = Intent(this@MainActivity, ConvoyLocationService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(svc)
        else startService(svc)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        store = SessionStore(this)

        lifecycleScope.launch {
            val key = store.sessionKey.first()
            if (key == null) {
                showAuthChoice()
            } else {
                showMainPlaceholder()
            }
        }
    }

    private fun restartToAuth() {
        stopService(Intent(this, ConvoyLocationService::class.java))
        lifecycleScope.launch {
            store.clearAll()
            val i = Intent(this@MainActivity, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
            finish()
        }
    }


    private fun showAuthChoice(){
        setContentView(R.layout.view_auth_choice)

        findViewById<Button>(R.id.btnCreate).setOnClickListener {
            //go to the register screen
            showRegisterDialog()
        }

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            //go to Login screen
            showLoginDialog()
        }
    }

    private fun showMainPlaceholder(){
        setContentView(R.layout.activity_main)
        //this become the map screen
        val tvConvoyId = findViewById<android.widget.TextView>(R.id.tvConvoyId)
        val btnStart = findViewById<Button>(R.id.btnStartConvoy)
        val btnJoin = findViewById<Button>(R.id.btnJoinConvoy)
        val btnLeave = findViewById<Button>(R.id.btnLeaveConvoy)
        val btnEnd = findViewById<Button>(R.id.btnEndConvoy)

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            restartToAuth()
        }

        btnStart.setOnClickListener {
            lifecycleScope.launch {
                val username = store.username.first()
                val sessionKey = store.sessionKey.first()

                if (username == null || sessionKey == null) {
                    android.widget.Toast.makeText(this@MainActivity, "Not logged in", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }

                try {
                    val resp = ApiClient.api.convoy(
                        action = "CREATE",
                        username = username,
                        sessionKey = sessionKey,
                        convoyId = null
                    )

                    val status = resp["status"]?.toString()
                    if (status == "SUCCESS") {
                        val convoyId = resp["convoy_id"]?.toString()
                        tvConvoyId.text = "Convoy: $convoyId"
                        activeConvoyId = convoyId
                        btnStart.visibility = View.GONE
                        btnEnd.visibility = View.VISIBLE

                        val svc = Intent(this@MainActivity, ConvoyLocationService::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= 26) {
                            startForegroundService(svc)
                        } else {
                            startService(svc)
                        }


                        android.widget.Toast.makeText(this@MainActivity, "Convoy started", android.widget.Toast.LENGTH_SHORT).show()
                        // later: start foreground service here
                    } else {
                        android.widget.Toast.makeText(this@MainActivity, resp["message"]?.toString() ?: "Error", android.widget.Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this@MainActivity, "Network error", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
        btnEnd.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("End Convoy?")
                .setMessage("Are you sure you want to end this convoy?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("End") { _, _ ->
                    lifecycleScope.launch {
                        val username = store.username.first()
                        val sessionKey = store.sessionKey.first()
                        val convoyId = activeConvoyId

                        if (username != null && sessionKey != null && convoyId != null) {
                            try {
                                val resp = ApiClient.api.convoy(
                                    action = "END",
                                    username = username,
                                    sessionKey = sessionKey,
                                    convoyId = convoyId
                                )

                                if (resp["status"]?.toString() == "SUCCESS") {
                                    stopService(Intent(this@MainActivity, ConvoyLocationService::class.java))
                                    setConvoyUI(null, tvConvoyId, btnStart, btnEnd)
                                    android.widget.Toast.makeText(this@MainActivity, "Convoy ended", android.widget.Toast.LENGTH_SHORT).show()
                                }

                            } catch (e: Exception) {
                                android.widget.Toast.makeText(this@MainActivity, "Error ending convoy", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                .show()
        }
        btnJoin.setOnClickListener {
            val input = android.widget.EditText(this)
            input.hint = "Enter convoy id"

            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Join Convoy")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Join") { _, _ ->
                    val id = input.text.toString().trim()
                    if (id.isEmpty()) {
                        android.widget.Toast.makeText(this, "Enter a convoy id", android.widget.Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    // feature can be incomplete, but UI must behave:
                    setConvoyUI(id, tvConvoyId, btnStart, btnEnd)
                    startConvoyService()
                }
                .show()
        }


        btnLeave.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Leave convoy?")
                .setMessage("Stop sharing location and leave this convoy?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK") { _, _ ->
                    stopService(Intent(this@MainActivity, ConvoyLocationService::class.java))
                    setConvoyUI(null, tvConvoyId, btnStart, btnEnd)
                }
                .show()
        }


        val mapFrag = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFrag.getMapAsync { map ->
            gMap = map
            ensureLocationPermissionAndStart()
        }

        lifecycleScope.launch {
            val username = store.username.first()
            val sessionKey = store.sessionKey.first()

            if (username != null && sessionKey != null) {
                try {
                    val resp = ApiClient.api.convoy(
                        action = "QUERY",
                        username = username,
                        sessionKey = sessionKey,
                        convoyId = null
                    )

                    if (resp["status"]?.toString() == "SUCCESS") {
                        val convoyId = resp["convoy_id"]?.toString()
                        val isActive = resp["active"]?.toString()?.toBoolean() ?: false

                        if (isActive && convoyId != null) {
                            setConvoyUI(convoyId, tvConvoyId, btnStart, btnEnd)
                            startConvoyService()

                        }
                    }
                } catch (e: Exception) {
                    // Ignore - no active convoy
                }
            }


    }




    }
    private fun ensureLocationPermissionAndStart() {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_REQ
            )
            return
        }

        startOneShotLocation()
    }



    private fun startOneShotLocation() {
        val fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            return
        }

        fused.lastLocation
            .addOnSuccessListener { loc ->
                if (loc == null) return@addOnSuccessListener

                val pos = LatLng(loc.latitude, loc.longitude)

                if (userMarker == null) {
                    userMarker = gMap?.addMarker(MarkerOptions().position(pos).title("You"))
                    gMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f))
                } else {
                    userMarker?.position = pos
                }
            }
            .addOnFailureListener {
                //  Toast/log
            }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_REQ && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            startOneShotLocation()
        }
    }





    private fun showRegisterDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_register, null)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Create Account")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", null)
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val first = view.findViewById<android.widget.EditText>(R.id.etFirst).text.toString().trim()
                val last = view.findViewById<android.widget.EditText>(R.id.etLast).text.toString().trim()
                val user = view.findViewById<android.widget.EditText>(R.id.etUsername).text.toString().trim()
                val pass = view.findViewById<android.widget.EditText>(R.id.etPassword).text.toString().trim()

                if (first.isEmpty() || last.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                    android.widget.Toast.makeText(this@MainActivity, "Fill in all fields", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                //  call REGISTER API here
                lifecycleScope.launch {
                    try {
                        val response = ApiClient.api.account(
                            action = "REGISTER",
                            username = user,
                            password = pass,
                            firstname = first,
                            lastname = last,
                            sessionKey = null
                        )
                        val status = response["status"]?.toString()
                        if (status == "SUCCESS") {
                            val sessionKey = response["session_key"].toString()

                            store.saveLogin(user, first, last, sessionKey)

                            runOnUiThread {
                                android.widget.Toast.makeText(this@MainActivity, "Account created!", android.widget.Toast.LENGTH_SHORT).show()

                                dialog.dismiss()
                                showMainPlaceholder()
                            }
                        }else {
                            val message = response["message"].toString()
                            android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_LONG).show()
                        }

                    } catch (e: Exception) {
                        android.widget.Toast.makeText(this@MainActivity, "Network error", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showLoginDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_login, null)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Log In")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Login", null)
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {

                val user = view.findViewById<android.widget.EditText>(R.id.etUsername).text.toString().trim()
                val pass = view.findViewById<android.widget.EditText>(R.id.etPassword).text.toString().trim()

                if (user.isEmpty() || pass.isEmpty()) {
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "Fill in all fields",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    try {
                        val response = ApiClient.api.account(
                            action = "LOGIN",
                            username = user,
                            password = pass,
                            firstname = null,
                            lastname = null,
                            sessionKey = null
                        )

                        val status = response["status"]?.toString()

                        if (status == "SUCCESS") {

                            val sessionKey = response["session_key"].toString()

                            store.saveLogin(user, null, null, sessionKey)

                            runOnUiThread {
                                android.widget.Toast.makeText(
                                    this@MainActivity,
                                    "Login successful!",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()

                                dialog.dismiss()
                                showMainPlaceholder()
                            }

                        }else {
                            val message = response["message"]?.toString()
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                message,
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }

                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Network error",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        dialog.show()
    }
    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ConvoyLocationService.ACTION_LOCATION) return

            val lat = intent.getDoubleExtra(ConvoyLocationService.EXTRA_LAT, 0.0)
            val lng = intent.getDoubleExtra(ConvoyLocationService.EXTRA_LNG, 0.0)
            val pos = LatLng(lat, lng)

            if (userMarker == null) {
                userMarker = gMap?.addMarker(MarkerOptions().position(pos).title("You"))
                gMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f))
            } else {
                userMarker?.position = pos
            }
        }
    }
    override fun onStart() {
        super.onStart()

        val filter = IntentFilter(ConvoyLocationService.ACTION_LOCATION)

        ContextCompat.registerReceiver(
            this,
            locationReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }


    override fun onStop() {
        super.onStop()
        unregisterReceiver(locationReceiver)
    }







}