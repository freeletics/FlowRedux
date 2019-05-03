package com.freeletics.rxredux

import com.freeletics.rxredux.businesslogic.github.GithubApi
import com.freeletics.rxredux.businesslogic.pagination.PaginationStateMachine
import com.freeletics.rxredux.di.ApplicationModule
import dagger.Component
import javax.inject.Singleton

@Singleton
@Component(modules =[ApplicationModule::class] )
interface TestComponent {

    fun paginationStateMachine(): PaginationStateMachine

    fun airplaceModeDecoratedGithubApi() : GithubApi
}
