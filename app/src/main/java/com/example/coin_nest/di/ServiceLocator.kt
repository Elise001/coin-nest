package com.example.coin_nest.di

import android.content.Context
import com.example.coin_nest.data.CoinNestRepository

object ServiceLocator {
    @Volatile
    private var initialized = false
    private lateinit var repositoryRef: CoinNestRepository

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            repositoryRef = CoinNestRepository(context.applicationContext)
            initialized = true
        }
    }

    fun repository(): CoinNestRepository = repositoryRef
}
