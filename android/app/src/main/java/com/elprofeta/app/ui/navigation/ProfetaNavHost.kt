package com.elprofeta.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.elprofeta.app.ui.news.ArticleDetailScreen
import com.elprofeta.app.ui.news.NewsScreen
import com.elprofeta.app.ui.news.NewsViewModel

private const val ROUTE_FEED = "feed"
private const val ROUTE_ARTICLE = "article"
private const val ARG_ARTICLE_ID = "articleId"

/** Navegacion de la app: portada -> noticia. */
@Composable
fun ProfetaNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    // Un unico ViewModel para las dos pantallas: la edicion ya esta en memoria
    // y el detalle no necesita volver a pedirla.
    val viewModel: NewsViewModel = viewModel(factory = NewsViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = ROUTE_FEED,
        modifier = modifier,
    ) {
        composable(ROUTE_FEED) {
            NewsScreen(
                state = state,
                onArticleClick = { articleId ->
                    navController.navigate("$ROUTE_ARTICLE/$articleId")
                },
                onRetry = viewModel::refresh,
            )
        }

        composable(
            route = "$ROUTE_ARTICLE/{$ARG_ARTICLE_ID}",
            arguments = listOf(navArgument(ARG_ARTICLE_ID) { type = NavType.IntType }),
        ) { backStackEntry ->
            val articleId = backStackEntry.arguments?.getInt(ARG_ARTICLE_ID) ?: -1
            ArticleDetailScreen(
                article = viewModel.articleById(articleId),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
