package name.me.voxhub

import android.app.Application
import name.me.voxhub.di.VoxHubContainer

class VoxHubApplication : Application() {
    lateinit var diagnostics: name.me.voxhub.diagnostics.LocalDiagnostics
        private set

    lateinit var container: VoxHubContainer
        private set

    override fun onCreate() {
        super.onCreate()
        diagnostics = name.me.voxhub.diagnostics.LocalDiagnostics(this)
        diagnostics.initialize()
        container = VoxHubContainer(this)
    }
}
