package com.akai.service

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*

/**
 * NearbySyncService — offline, device-to-device conversation sync.
 *
 * Uses Google Nearby Connections (Bluetooth + Wi-Fi Direct under the hood) so two
 * phones can share one conversation with NO internet, NO Wi-Fi router, NO server.
 *
 * Privacy (RA 10173): only conversation TEXT (a ConversationEntry as JSON) is ever
 * transmitted. Camera frames, audio, and the ML models never leave the device.
 * Nothing is written to disk here — payloads live only in memory for the session.
 *
 * Roles:
 *   - HOST  ("Start Session") -> advertise() under a session code, wait for a joiner.
 *   - GUEST ("Join Session")  -> discover() and connect to the matching session code.
 *
 * The service is transport-only. It does not know what a message means — it just
 * ships a String to the other phone and hands back Strings that arrive. The
 * ViewModel decides how to turn those into ConversationEntry objects.
 */
class NearbySyncService(private val context: Context) {

    companion object {
        private const val TAG = "NearbySyncService"
        // Must be identical on both phones or they won't find each other.
        private const val SERVICE_ID = "com.akai.SESSION"
        // P2P_STAR = one host + one or more guests, all talking to the host. Perfect for 1:1.
        private val STRATEGY = Strategy.P2P_STAR
    }

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)

    /** The session code this device is hosting or trying to join (e.g. "AK-4829"). */
    private var sessionCode: String = ""
    /** endpointId of the connected peer, once a connection is established. */
    private var connectedEndpointId: String? = null

    // ---- Callbacks the ViewModel wires up (all fire on the main thread) ----
    /** A text payload arrived from the other phone. */
    var onMessageReceived: ((String) -> Unit)? = null
    /** Connection to the peer is fully established and ready to send. */
    var onConnected: (() -> Unit)? = null
    /** Peer disconnected, or the connection dropped/ended. */
    var onDisconnected: (() -> Unit)? = null
    /** Something went wrong (advertise/discover/connect failed). Message is user-safe. */
    var onError: ((String) -> Unit)? = null

    // =====================================================================
    //  HOST — "Start Session"
    // =====================================================================
    fun startHosting(code: String) {
        sessionCode = code
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        // We advertise using the session code as the endpoint name, so the guest
        // can match on it and connect to the RIGHT host (not a random nearby phone).
        client.startAdvertising(code, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener { Log.d(TAG, "Advertising session $code") }
            .addOnFailureListener { e ->
                Log.e(TAG, "startAdvertising failed", e)
                onError?.invoke("Could not start the session. Check Bluetooth/Wi-Fi and try again.")
            }
    }

    // =====================================================================
    //  GUEST — "Join Session"
    // =====================================================================
    fun joinSession(code: String) {
        sessionCode = code
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        client.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener { Log.d(TAG, "Discovering for session $code") }
            .addOnFailureListener { e ->
                Log.e(TAG, "startDiscovery failed", e)
                onError?.invoke("Could not search for the session. Check Bluetooth/Wi-Fi and try again.")
            }
    }

    // Guest side: found a nearby host. Only connect if its advertised name matches
    // the session code the user typed — this is what makes AK-4829 join AK-4829.
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.endpointName == sessionCode) {
                Log.d(TAG, "Found matching host for $sessionCode, requesting connection")
                client.stopDiscovery() // stop scanning once we've found our host
                client.requestConnection(sessionCode, endpointId, connectionLifecycleCallback)
                    .addOnFailureListener { e ->
                        Log.e(TAG, "requestConnection failed", e)
                        onError?.invoke("Found the session but couldn't connect. Try again.")
                    }
            } else {
                Log.d(TAG, "Ignoring endpoint ${info.endpointName} (want $sessionCode)")
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
        }
    }

    // =====================================================================
    //  CONNECTION LIFECYCLE (shared by host + guest)
    // =====================================================================
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept: this is a local, user-initiated session (they exchanged the
            // code in person), so we don't prompt for a PIN. Simpler UX for the demo.
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connected to $endpointId")
                    connectedEndpointId = endpointId
                    // Once connected we no longer need to advertise/discover.
                    client.stopAdvertising()
                    client.stopDiscovery()
                    onConnected?.invoke()
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED ->
                    onError?.invoke("The other device rejected the connection.")
                else ->
                    onError?.invoke("Connection failed. Please try again.")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            connectedEndpointId = null
            onDisconnected?.invoke()
        }
    }

    // =====================================================================
    //  SENDING + RECEIVING MESSAGES
    // =====================================================================
    /** Send one conversation entry (already JSON) to the paired device. */
    fun sendMessage(json: String) {
        val endpoint = connectedEndpointId
        if (endpoint == null) {
            Log.w(TAG, "sendMessage called with no connected peer — ignoring")
            return
        }
        val payload = Payload.fromBytes(json.toByteArray(Charsets.UTF_8))
        client.sendPayload(endpoint, payload)
    }

    // Receives payloads from the peer and forwards the text up to the ViewModel.
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val json = String(bytes, Charsets.UTF_8)
                onMessageReceived?.invoke(json)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Not needed for tiny text payloads — they arrive in one shot.
        }
    }

    // =====================================================================
    //  TEARDOWN — session end (RA 10173: nothing persists)
    // =====================================================================
    /** End the session and release everything. Safe to call multiple times. */
    fun stop() {
        try {
            client.stopAdvertising()
            client.stopDiscovery()
            client.stopAllEndpoints() // disconnects the peer too
        } catch (e: Exception) {
            Log.e(TAG, "Error during stop()", e)
        }
        connectedEndpointId = null
        sessionCode = ""
    }

    fun isConnected(): Boolean = connectedEndpointId != null
}
