package com.istudio.articleskmp.articles.di

import com.istudio.articleskmp.articles.ArticlesService
import com.istudio.articleskmp.articles.ArticlesUseCase
import com.istudio.articleskmp.articles.ArticlesViewModel
import org.koin.dsl.module

val articlesModule = module {

    single<ArticlesService> { ArticlesService(get()) }
    single<ArticlesUseCase> { ArticlesUseCase(get()) }
    single<ArticlesViewModel> { ArticlesViewModel(get()) }
}