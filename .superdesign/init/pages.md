# UI dependency trees

## DashScreen
Entry: app/src/main/java/com/example/navisarthi/ui/screens/DashScreen.kt
Local imports:
import com.example.navisarthi.ui.NaviSarthiIcons
import com.example.navisarthi.ui.components.*
import com.example.navisarthi.ui.theme.*
import com.example.navisarthi.viewmodel.ConnStage
import com.example.navisarthi.viewmodel.DashViewModel

## DriveBackupSettings
Entry: app/src/main/java/com/example/navisarthi/ui/screens/DriveBackupSettings.kt
Local imports:
import com.example.navisarthi.data.DriveBackup
import com.example.navisarthi.ui.components.*
import com.example.navisarthi.ui.theme.TextMid

## GarageScreen
Entry: app/src/main/java/com/example/navisarthi/ui/screens/GarageScreen.kt
Local imports:
import com.example.navisarthi.data.BikeIdentity
import com.example.navisarthi.data.FuelFillup
import com.example.navisarthi.data.MaintenanceItem
import com.example.navisarthi.data.ScheduledService
import com.example.navisarthi.data.ServiceRecord
import com.example.navisarthi.data.VehicleDocument
import com.example.navisarthi.ui.NaviSarthiIcons
import com.example.navisarthi.ui.components.*
import com.example.navisarthi.ui.theme.*
import com.example.navisarthi.viewmodel.GarageUi
import com.example.navisarthi.viewmodel.GarageViewModel
import com.example.navisarthi.viewmodel.MaintRow

## HomeScreen
Entry: app/src/main/java/com/example/navisarthi/ui/screens/HomeScreen.kt
Local imports:
import com.example.navisarthi.BuildConfig
import com.example.navisarthi.util.BuildId
import com.example.navisarthi.ui.NaviSarthiIcons
import com.example.navisarthi.ui.components.*
import com.example.navisarthi.ui.theme.*
import com.example.navisarthi.viewmodel.ConnectionState
import com.example.navisarthi.viewmodel.RouteViewModel

## LoginScreen
Entry: app/src/main/java/com/example/navisarthi/ui/screens/LoginScreen.kt
Local imports:
import com.example.navisarthi.ui.components.CircularDash
import com.example.navisarthi.ui.theme.*
import com.example.navisarthi.viewmodel.AuthViewModel

## RidesScreen
Entry: app/src/main/java/com/example/navisarthi/ui/screens/RidesScreen.kt
Local imports:
import com.example.navisarthi.data.Ride
import com.example.navisarthi.dash.nav.PolylineCodec
import com.example.navisarthi.ui.NaviSarthiIcons
import com.example.navisarthi.ui.components.*
import com.example.navisarthi.ui.theme.*
import com.example.navisarthi.viewmodel.RidesViewModel

## RouteScreen
Entry: app/src/main/java/com/example/navisarthi/ui/screens/RouteScreen.kt
Local imports:
import com.example.navisarthi.ui.NaviSarthiIcons
import com.example.navisarthi.ui.components.*
import com.example.navisarthi.ui.theme.*
import com.example.navisarthi.viewmodel.RouteViewModel

## SettingsScreen
Entry: app/src/main/java/com/example/navisarthi/ui/screens/SettingsScreen.kt
Local imports:
import com.example.navisarthi.ui.NaviSarthiIcons
import com.example.navisarthi.ui.components.*
import com.example.navisarthi.ui.theme.*
import com.example.navisarthi.viewmodel.ConnectionState
import com.example.navisarthi.viewmodel.DashViewModel
Shared dependency closure: ui/components/*.kt -> ui/theme/*.kt and ui/NaviSarthiIcons.kt. Theme -> VehicleModel, Color, Type; custom vector icons are self-contained. Screen viewmodels/data/dash imports handle application state; exclude runtime logic from design payload. Home render: HomeScreen -> ScreenHeader, NaviSarthiCard, CircularDash, ControlSection/Hint, NaviSarthiBtn/Chip/Row/Divider -> theme and icons. AppNavigation supplies bottom tab shell. No web routes or CSS.
