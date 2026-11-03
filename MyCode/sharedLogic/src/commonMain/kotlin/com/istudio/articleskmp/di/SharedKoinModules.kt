package com.istudio.articleskmp.di

import com.istudio.articleskmp.articles.di.articlesModule

val sharedKoinModules = listOf(
    articlesModule,
    networkModule
)