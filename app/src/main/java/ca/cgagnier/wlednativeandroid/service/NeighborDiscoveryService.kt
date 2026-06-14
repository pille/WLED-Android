package ca.cgagnier.wlednativeandroid.service

import android.util.Log
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import javax.inject.Inject

private const val TAG = "NeighborDiscoveryService"
private const val MAX_DISCOVERY_DEPTH = 3 // Prevent infinite loops in circular networks
private const val DISCOVERY_TIMEOUT_SECONDS = 10L

/**
 * Service for discovering WLED devices across routed networks by querying
 * the /json/nodes API endpoint on already-known devices.
 */
class NeighborDiscoveryService @Inject constructor(
    private val deviceApiFactory: DeviceApiFactory,
) {

    /**
     * Discovers neighbor WLED instances from a known device.
     * Recursively discovers neighbors up to MAX_DISCOVERY_DEPTH levels.
     *
     * @param address The address of a known WLED device
     * @param visitedAddresses Set of already-visited addresses to prevent infinite loops
     * @param depth Current recursion depth
     * @return List of discovered neighbor addresses (IPs)
     */
    suspend fun discoverNeighbors(
        address: String,
        visitedAddresses: MutableSet<String> = mutableSetOf(),
        depth: Int = 0,
    ): List<String> {
        if (depth >= MAX_DISCOVERY_DEPTH || address in visitedAddresses) {
            return emptyList()
        }

        visitedAddresses.add(address)
        val discoveredAddresses = mutableListOf<String>()

        try {
            val api = deviceApiFactory.create(address, DISCOVERY_TIMEOUT_SECONDS)
            val response = api.getNodes()

            if (!response.isSuccessful) {
                Log.w(TAG, "Failed to get nodes from $address: ${response.code()}")
                return emptyList()
            }

            val nodes = response.body() ?: return emptyList()

            for (node in nodes) {
                if (node.ip.isNullOrEmpty()) {
                    Log.w(TAG, "Node from $address has no IP: $node")
                    continue
                }

                // Normalize IP to a standard format for checking
                val normalizedIp = node.ip!!.trim()
                if (normalizedIp !in visitedAddresses && isValidIpAddress(normalizedIp)) {
                    discoveredAddresses.add(normalizedIp)
                    Log.d(TAG, "Discovered neighbor node: $normalizedIp (name: ${node.name})")

                    // Recursively discover neighbors from this device
                    if (depth < MAX_DISCOVERY_DEPTH - 1) {
                        try {
                            val subNeighbors = discoverNeighbors(normalizedIp, visitedAddresses, depth + 1)
                            discoveredAddresses.addAll(subNeighbors)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to discover neighbors from $normalizedIp: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering neighbors from $address: ${e.message}", e)
        }

        return discoveredAddresses
    }

    /**
     * Discovers all neighbors from a list of known devices.
     *
     * @param addresses List of known device addresses
     * @return Set of all discovered neighbor addresses
     */
    suspend fun discoverNeighborsFromMultiple(addresses: List<String>): Set<String> {
        val allDiscovered = mutableSetOf<String>()
        val visited = mutableSetOf<String>()

        for (address in addresses) {
            try {
                val neighbors = discoverNeighbors(address, visited)
                allDiscovered.addAll(neighbors)
            } catch (e: Exception) {
                Log.w(TAG, "Error discovering from $address: ${e.message}")
            }
        }

        return allDiscovered
    }

    /**
     * Validates if a string is a reasonable IPv4 address format.
     */
    private fun isValidIpAddress(ip: String): Boolean {
        return try {
            val parts = ip.split(".")
            if (parts.size != 4) return false
            parts.all { part ->
                val num = part.toIntOrNull()
                num != null && num in 0..255
            }
        } catch (e: Exception) {
            false
        }
    }
}
