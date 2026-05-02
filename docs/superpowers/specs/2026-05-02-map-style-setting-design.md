# Map Style Setting Design

## Goal

Add a persisted Settings control that lets the user switch between the bundled map styles staged in
`feature/map/src/main/assets/map/styles`.

## Approved Behavior

The app exposes three bundled styles:

- `cyberpunk`: Cyberpunk, backed by `asset://map/styles/cyberpunk.json`
- `light`: Light, backed by `asset://map/styles/light.json`
- `urban-noir`: Urban Noir, backed by `asset://map/styles/urban-noir.json`

Cyberpunk is the default when no preference has been saved or when a saved style id is unknown.

## Architecture

The selected style is app settings data, so the domain layer owns the repository contract and use case boundary.
The data layer persists only the selected style id using Preferences DataStore. Feature modules consume the domain
use cases rather than reading DataStore directly.

Settings observes available styles plus the selected style and writes changes through a `SetSelectedMapStyleUseCase`.
Map observes the selected style through `MapViewModel`, includes the asset URI in `MapUiState.Content`, and passes it
to `MapLibreViewMapScreen`. The MapLibre interop reloads the Android view when the style URI changes.

## Testing

Focused JVM tests cover style catalog defaults, persistence fallback behavior, SettingsViewModel selection state and
write intent, and MapViewModel exposure of the selected style URI. Existing Compose tests are updated for the new
state field.
