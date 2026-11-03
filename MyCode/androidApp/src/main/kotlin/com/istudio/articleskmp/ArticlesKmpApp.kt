package com.istudio.articleskmp

import android.app.Application
import com.istudio.articleskmp.di.sharedKoinModules
import com.istudio.articleskmp.di.viewModelsModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin

class ArticlesKmpApp: Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin()
    }

    private fun initKoin() {
        val modules = sharedKoinModules + viewModelsModule

        startKoin {
            androidContext(this@ArticlesKmpApp)
            modules(modules)
        }
    }
}