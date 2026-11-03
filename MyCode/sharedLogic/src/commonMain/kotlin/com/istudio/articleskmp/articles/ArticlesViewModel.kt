package com.istudio.articleskmp.articles

import com.istudio.articleskmp.BaseViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

class ArticlesViewModel : BaseViewModel() {

    private val _articlesState: MutableStateFlow<ArticleState> = MutableStateFlow(ArticleState(articles = emptyList(), loading = true))
    val articlesState: StateFlow<ArticleState> get() = _articlesState

    private val useCase: ArticlesUseCase

    init {
        val httpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
        }
        val service = ArticlesService(httpClient)
        useCase = ArticlesUseCase(service)

        getArticles()
    }

    private fun getArticles(){
        scope.launch {
            val fetchArticles =  useCase.getArticles()
            _articlesState.emit(ArticleState(articles = fetchArticles))
        }
    }

}