# Routed Network Discovery Implementation

This feature implements automatic discovery of WLED devices across routed networks by querying the `/json/nodes` API endpoint on known devices.

## Files Added

1. **NodeInfo.kt** - Data model for `/json/nodes` API response
2. **DeviceApi.kt** - Updated with `getNodes()` endpoint and timeout variant
3. **NeighborDiscoveryService.kt** - Core service for discovering neighbors recursively
4. **RoutedNetworkDiscoveryViewModel.kt** - ViewModel managing discovery state
5. **RoutedNetworkDiscoveryCard.kt** - Composable UI component
6. **strings.xml** - Localization strings

## How to Use

### Integration in Your UI

Add the `RoutedNetworkDiscoveryCard` to your home screen or settings screen:

```kotlin
// In your main composable (e.g., HomeScreen.kt)
@Composable
fun HomeScreen() {
    Column {
        // ... existing content ...
        
        // Add this card wherever appropriate
        RoutedNetworkDiscoveryCard()
    }
}
```

### Manual Testing

1. **Build and run the app:**
   ```bash
   ./gradlew build
   ```

2. **Test with multiple WLED devices:**
   - Set up at least 2 WLED devices on different networks/subnets
   - Ensure your Android device can reach both subnets
   - Add one device manually to establish it as a known device
   - Click "Discover Routed Network Devices" button
   - The app will query that device for neighbors via `/json/nodes`

3. **Expected behavior:**
   - Button shows loading state while discovering
   - Displays count of discovered and added devices
   - Skips devices that already exist
   - Shows error messages if discovery fails

### Code Quality Checks

Before committing, run:

```bash
# Format code
./gradlew spotlessApply

# Check for issues
./gradlew detekt

# Run tests
./gradlew test
```

## How It Works

### Discovery Flow

1. User clicks "Discover Routed Network Devices" button
2. ViewModel collects all known device addresses
3. For each known device:
   - Queries `/json/nodes` API endpoint
   - Parses NodeInfo responses
   - Validates IP addresses
   - Recursively discovers neighbors (up to 3 levels deep)
   - Prevents infinite loops by tracking visited addresses
4. For each discovered device:
   - Checks if it already exists in database
   - If new, fetches device info and adds it
   - Updates UI with count of discovered/added devices

### Key Features

- **Recursive discovery**: Discovers neighbors of neighbors (configurable depth limit)
- **Loop prevention**: Tracks visited addresses to avoid infinite loops
- **Network timeout**: 10-second timeout per device query to prevent hanging
- **Graceful degradation**: Continues even if some devices fail
- **Duplicate prevention**: Skips devices that already exist
- **Error handling**: Displays user-friendly error messages

## Configuration

Modify these constants in `NeighborDiscoveryService.kt`:

```kotlin
private const val MAX_DISCOVERY_DEPTH = 3           // Maximum recursion depth
private const val DISCOVERY_TIMEOUT_SECONDS = 10L   // Timeout per device
```

## Testing Checklist

- [ ] App compiles without errors
- [ ] `spotlessCheck` passes
- [ ] `detekt` passes
- [ ] Can add devices manually
- [ ] Can click discovery button without crashes
- [ ] Discovery finds devices in routed network
- [ ] UI updates with discovery results
- [ ] Error messages display correctly
- [ ] Button is disabled during discovery
