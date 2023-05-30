package com.doubtless.doubtless.screens.dashboard

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.doubtless.doubtless.analytics.AnalyticsTracker
import com.doubtless.doubtless.screens.auth.usecases.UserManager
import com.doubtless.doubtless.screens.dashboard.usecases.FetchUserDataUseCase
import com.doubtless.doubtless.screens.home.FeedEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val fetchUserDataUseCase: FetchUserDataUseCase,
    private val analyticsTracker: AnalyticsTracker,
    private val userManager: UserManager
) : ViewModel() {
    private val _homeEntities = mutableListOf<FeedEntity>()
    val homeEntities: List<FeedEntity> = _homeEntities

    private var isLoading = false

    private val _fetchedHomeEntities = MutableLiveData<List<FeedEntity>?>()
    val fetchedHomeEntities: LiveData<List<FeedEntity>?> =
        _fetchedHomeEntities // TODO : use Result here!

    fun notifyFetchedDoubtsConsumed() {
        _fetchedHomeEntities.postValue(null)
    }

    fun fetchDoubts(isRefreshCall: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {

        if (isLoading) return@launch

        isLoading = true
        val result = fetchUserDataUseCase.fetchFeedSync(
            request = FetchUserDataUseCase.FetchUserFeedRequest(
                user = userManager.getCachedUserData()!!, fetchFromPage1 = true
            )
        )

        Log.i("result", result.toString())
        if (result is FetchUserDataUseCase.Result.Success && result.data.isNotEmpty()) {

            if (!isRefreshCall) {
                analyticsTracker.trackFeedNextPage(homeEntities.size)
            } else {
                analyticsTracker.trackFeedRefresh()
            }

            val homeEntitiesFromServer = mutableListOf<FeedEntity>()

            result.data.forEach {
                homeEntitiesFromServer.add(it.toHomeEntity())
            }


            _homeEntities.addAll(homeEntitiesFromServer)
            _fetchedHomeEntities.postValue(_homeEntities)

        } else {
            // ERROR CASE
            _fetchedHomeEntities.postValue(null)
        }

        Log.i("doubt list", fetchedHomeEntities.value?.toTypedArray().contentToString())
        isLoading = false
    }

    fun refreshList() {
        _homeEntities.clear()
        fetchDoubts(isRefreshCall = true)
    }

    companion object {
        class Factory constructor(
            private val fetchUserDataUseCase: FetchUserDataUseCase,
            private val analyticsTracker: AnalyticsTracker,
            private val userManager: UserManager
        ) : ViewModelProvider.Factory {

            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DashboardViewModel(
                    fetchUserDataUseCase, analyticsTracker, userManager
                ) as T
            }
        }
    }


}