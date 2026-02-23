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
import android.os.Build
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
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import edu.temple.convoy.ApiClient
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat.registerReceiver
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject


class MainActivity : AppCompatActivity() {


    private lateinit var store: SessionStore
    private var gMap: GoogleMap? = null
    private var userMarker: Marker? = null
    private val LOCATION_REQ = 1001
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var activeConvoyId: String? = null

    private val convoyMarkers = mutableMapOf<String, Marker>()

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
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc)
        else startService(svc)
    }

    private val convoyUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val payload = intent.getStringExtra(MyFirebaseMessagingService.EXTRA_PAYLOAD) ?: return
            val json = JSONObject(payload)
            val action = json.optString("action")
            Log.d("FCM", "Update REceived $payload")
            if (action == "UPDATE") {
                val dataArray = json.getJSONArray("data")
                updateConvoyMarkers(dataArray)
            } else if (action == "END"){
                handleConvoyEnd()
            }
        }
    }

    private fun updateConvoyMarkers(data: JSONArray) {
        val currentUsersInPayload = mutableSetOf<String>()
        val builder = LatLngBounds.Builder()

        userMarker?.position?.let { builder.include(it) }

        lifecycleScope.launch {
            val myUsername = store.username.first()

            for (i in 0 until data.length()) {
                val userJson = data.getJSONObject(i)
                val username = userJson.getString("username")

                if (username == myUsername) continue

                currentUsersInPayload.add(username)
                val lat = userJson.getDouble("latitude")
                val lng = userJson.getDouble("longitude")
                val pos = LatLng(lat, lng)
                builder.include(pos)

                if (convoyMarkers.containsKey(username)) {
                    convoyMarkers[username]?.position = pos
                } else {
                    val marker = gMap?.addMarker(MarkerOptions()
                        .position(pos)
                        .title(username)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    )
                    if (marker != null) convoyMarkers[username] = marker
                }
            }

            val iterator = convoyMarkers.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (!currentUsersInPayload.contains(entry.key)) {
                    entry.value.remove()
                    iterator.remove()
                }
            }

            if (data.length() > 0) {
                val bounds = builder.build()
                gMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
            }
        }
    }

    private fun handleConvoyEnd() {
        // Stop the location service
        stopService(Intent(this, ConvoyLocationService::class.java))

        // Clear all markers
        convoyMarkers.values.forEach { it.remove() }
        convoyMarkers.clear()

        // Reset UI
        lifecycleScope.launch {
            store.saveConvoyId(null)
            // Update your TextViews/Buttons here
        }
        Toast.makeText(this, "Convoy has ended", Toast.LENGTH_LONG).show()
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
        val tvConvoyId = findViewById<TextView>(R.id.tvConvoyId)
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
                    Toast.makeText(this@MainActivity, "Not logged in", Toast.LENGTH_SHORT).show()
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
                        store.saveConvoyId(convoyId)
                        store.saveCreatedConvoyId((convoyId))
                        btnStart.visibility = View.GONE
                        btnEnd.visibility = View.VISIBLE

                        val svc = Intent(this@MainActivity, ConvoyLocationService::class.java)
                        if (Build.VERSION.SDK_INT >= 26) {
                            startForegroundService(svc)
                        } else {
                            startService(svc)
                        }


                        Toast.makeText(this@MainActivity, "Convoy started", Toast.LENGTH_SHORT).show()
                        // later: start foreground service here
                    } else {
                        Toast.makeText(this@MainActivity, resp["message"]?.toString() ?: "Error", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_LONG).show()
                }
            }
        }
        btnEnd.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("End Convoy?")
                .setMessage("Are you sure you want to end this convoy?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("End") { _, _ ->
                    lifecycleScope.launch {
                        val username = store.username.first() ?: return@launch
                        val sessionKey = store.sessionKey.first() ?: return@launch
                        val convoyId = activeConvoyId ?: return@launch
                        try {
                            val resp = ApiClient.api.convoy(
                                action = "END",
                                username = username,
                                sessionKey = sessionKey,
                                convoyId = convoyId
                            )
                            if (resp["status"]?.toString() == "SUCCESS") {
                                store.saveConvoyId(null)
                                stopService(Intent(this@MainActivity, ConvoyLocationService::class.java))
                                setConvoyUI(null, tvConvoyId, btnStart, btnEnd)
                                Toast.makeText(this@MainActivity, "Convoy ended", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Error ending convoy", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.show()
        }
        btnJoin.setOnClickListener {
            val input = EditText(this)
            input.hint = "Enter convoy id"
            AlertDialog.Builder(this)
                .setTitle("Join Convoy")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Join") { _, _ ->
                    val id = input.text.toString().trim()
                    if (id.isEmpty()) {
                        Toast.makeText(this, "Enter a convoy id", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    lifecycleScope.launch {
                        val username = store.username.first() ?: return@launch
                        val sessionKey = store.sessionKey.first() ?: return@launch
                        try {
                            val resp = ApiClient.api.convoy(
                                action = "JOIN",
                                username = username,
                                sessionKey = sessionKey,
                                convoyId = id
                            )
                            if (resp["status"]?.toString() == "SUCCESS") {
                                store.saveConvoyId(id)
                                store.saveCreatedConvoyId(null)
                                setConvoyUI(id, tvConvoyId, btnStart, btnEnd)
                                startConvoyService()
                            } else {
                                Toast.makeText(this@MainActivity,
                                    resp["message"]?.toString() ?: "Error joining",
                                    Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.show()
        }

        btnLeave.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Leave convoy?")
                .setMessage("Stop sharing location and leave this convoy?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK") { _, _ ->
                    lifecycleScope.launch {
                        val username = store.username.first() ?: return@launch
                        val sessionKey = store.sessionKey.first() ?: return@launch
                        val convoyId = activeConvoyId ?: return@launch
                        Log.d("LeaveTest", "Leave Started")
                        if (store.lastConvoyCreated.first() != null) {
                            Toast.makeText(this@MainActivity, "You can't leave a convoy you created. End the convoy first.", Toast.LENGTH_SHORT).show()
                            Log.d("LeaveTest", "Leave Failed")
                            return@launch
                        }
                        try {
                            ApiClient.api.convoy(
                                action = "LEAVE",
                                username = username,
                                sessionKey = sessionKey,
                                convoyId = convoyId
                            )
                        } catch (e: Exception) { }
                        store.saveConvoyId(null)
                        Log.d("LeaveTest", "Leave Success")
                        stopService(Intent(this@MainActivity, ConvoyLocationService::class.java))
                        setConvoyUI(null, tvConvoyId, btnStart, btnEnd)
                    }
                }.show()
        }


        val mapFrag = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFrag.getMapAsync { map ->
            gMap = map
            ensureLocationPermissionAndStart()
            askNotificationPermission()
            lifecycleScope.launch {
                val username = store.username.first()
                val sessionKey = store.sessionKey.first()
                val fcmToken = store.fcmToken.first()

                if (username != null && sessionKey != null && fcmToken != null) {
                    try {
                        ApiClient.api.account(
                            "UPDATE", username, null, fcmToken,
                            null, null, sessionKey
                        )
                        Log.d("FCMUpdate", "FCM Token Update Sent")
                    } catch (e: Exception) {
                        Log.e("FCMUpdate", "FCM Token Update failed", e)
                    }
                }
            }
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

                        if (isActive || convoyId != null) {
                            setConvoyUI(convoyId, tvConvoyId, btnStart, btnEnd)
                            startConvoyService()

                        }
                    }
                } catch (e: Exception) {
                }
            }


    }




    }

    private fun askNotificationPermission() {
        // This is only necessary for API level >= 33 (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                // FCM SDK (and your app) can post notifications.
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // TODO: display an educational UI explaining to the user the features that will be enabled
                //       by them granting the POST_NOTIFICATION permission. This UI should provide the user
                //       "OK" and "No thanks" buttons. If the user selects "OK," directly request the permission.
                //       If the user selects "No thanks," allow the user to continue without notifications.
            } else {
                // Directly ask for the permission
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w("FirebaseLogs", "Fetching FCM registration token failed", task.exception)
                    return@OnCompleteListener
                }

                // Get new FCM registration token
                val token = task.result


                Log.d("FirebaseLogs", token)
            })
        } else {
            // TODO: Inform user that that your app will not show notifications.
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
                Log.d("ConvoyLocation", "Listening")
                val pos = LatLng(loc.latitude, loc.longitude)

                if (userMarker == null) {
                    userMarker = gMap?.addMarker(MarkerOptions().position(pos).title("You"))
                    gMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f))
                } else {
                    userMarker?.position = pos
                }
            }
            .addOnFailureListener {
                Log.d("ConvoyLocation", "Failed")
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

        val dialog = AlertDialog.Builder(this)
            .setTitle("Create Account")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", null)
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {
                val first = view.findViewById<EditText>(R.id.etFirst).text.toString().trim()
                val last = view.findViewById<EditText>(R.id.etLast).text.toString().trim()
                val user = view.findViewById<EditText>(R.id.etUsername).text.toString().trim()
                val pass = view.findViewById<EditText>(R.id.etPassword).text.toString().trim()

                if (first.isEmpty() || last.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Fill in all fields", Toast.LENGTH_SHORT).show()
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
                            fcmToken = null,
                            sessionKey = null

                        )
                        val status = response["status"]?.toString()
                        if (status == "SUCCESS") {
                            val sessionKey = response["session_key"].toString()

                            store.saveLogin(user, first, last, sessionKey)

                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Account created!", Toast.LENGTH_SHORT).show()

                                dialog.dismiss()
                                showMainPlaceholder()
                            }
                        }else {
                            val message = response["message"].toString()
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                        }

                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Network error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showLoginDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_login, null)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Log In")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Login", null)
            .create()

        dialog.setOnShowListener {
            val btn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btn.setOnClickListener {

                val user = view.findViewById<EditText>(R.id.etUsername).text.toString().trim()
                val pass = view.findViewById<EditText>(R.id.etPassword).text.toString().trim()

                if (user.isEmpty() || pass.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Fill in all fields",
                        Toast.LENGTH_SHORT
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
                            fcmToken = null,
                            lastname = null,
                            sessionKey = null
                        )

                        val status = response["status"]?.toString()

                        if (status == "SUCCESS") {

                            val sessionKey = response["session_key"].toString()

                            store.saveLogin(user, null, null, sessionKey)

                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Login successful!",
                                    Toast.LENGTH_SHORT
                                ).show()

                                dialog.dismiss()
                                showMainPlaceholder()
                            }

                        }else {
                            val message = response["message"]?.toString()
                            Toast.makeText(
                                this@MainActivity,
                                message,
                                Toast.LENGTH_LONG
                            ).show()
                        }

                    } catch (e: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            "Network error",
                            Toast.LENGTH_LONG
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
            runOnUiThread {
                if (userMarker == null) {
                    userMarker = gMap?.addMarker(MarkerOptions()
                        .position(pos)
                        .title("You"))
                    gMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f))
                } else {
                    userMarker?.position = pos
                    gMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15f))
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ConvoyLocationService.ACTION_LOCATION)
        registerReceiver(
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