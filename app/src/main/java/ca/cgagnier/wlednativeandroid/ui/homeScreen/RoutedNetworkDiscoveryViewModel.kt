package ca.cgagnier.wlednativeandroid.ui.homeScreen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.DeviceFirstContactService
import ca.cgagnier.wlednativeandroid.service.NeighborDiscoveryService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "RoutedNetworkDiscoveryVM"

data class RoutedNetworkDiscoveryState(
    val isDiscovering: Boolean = false,
    val discoveredCount: Int = 0,
    val addedCount: Int = 0,
    val errorMessage: String? = null,
)

@HiltViewModel
class RoutedNetworkDiscoveryViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val neighborDiscoveryService: NeighborDiscoveryService,
    private val deviceFirstContactService: DeviceFirstContactService,
) : ViewModel() {

    private val _state = MutableStateFlow(RoutedNetworkDiscoveryState())
    val state = _state.asStateFlow()

    /**
     * Starts discovering neighbors from all known devices.
     */
    fun startDiscovery() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isDiscovering = true, errorMessage = null, discoveredCount = 0, addedCount = 0) }

            try {
                val knownDevices = deviceRepository.getAllDevices()
                if (knownDevices.isEmpty()) {
                    _state.update {
                        it.copy(
                            isDiscovering = false,
                            errorMessage = "No known devices to query",
                        )
                    }
                    return@launch
                }

                val addresses = knownDevices.map { it.address }
                Log.d(TAG, "Starting routed network discovery from ${addresses.size} known devices")

                val discoveredAddresses = neighborDiscoveryService.discoverNeighborsFromMultiple(addresses)
                _state.update { it.copy(discoveredCount = discoveredAddresses.size) }

                if (discoveredAddresses.isEmpty()) {
                    Log.i(TAG, "No new neighbors discovered")
                    _state.update { it.copy(isDiscovering = false) }
                    return@launch
                }

                // Attempt to add discovered devices
                var addedCount = 0
                for (discoveredAddress in discoveredAddresses) {
                    try {
                        // Check if device already exists
                        val existingDevice = deviceRepository.findDeviceByAddress(discoveredAddress)
                        if (existingDevice != null) {
                            Log.d(TAG, "Device already exists at $discoveredAddress")
                            continue
                        }

                        // Fetch and add the device
                        deviceFirstContactService.fetchAndUpsertDevice(discoveredAddress)
                        addedCount++
                        Log.i(TAG, "Successfully added device at $discoveredAddress")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to add device at $discoveredAddress: ${e.message}")
                    }
                }

                _state.update {
                    it.copy(
                        isDiscovering = false,
                        addedCount = addedCount,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Discovery failed: ${e.message}", e)
                _state.update {
                    it.copy(
                        isDiscovering = false,
                        errorMessage = e.message ?: "Unknown error during discovery",
                    )
                }
            }
        }
    }

    fun clearErrorMessage() {
        _state.update { it.copy(errorMessage = null) }
    }
}
