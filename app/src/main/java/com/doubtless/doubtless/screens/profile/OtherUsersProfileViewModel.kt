package com.doubtless.doubtless.screens.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.doubtless.doubtless.analytics.AnalyticsTracker
import com.doubtless.doubtless.screens.auth.User
import com.doubtless.doubtless.screens.dashboard.usecases.FetchUserDataUseCase
import com.doubtless.doubtless.screens.home.entities.FeedEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OtherUsersProfileViewModel(
    private val fetchUserDataUseCase: FetchUserDataUseCase,
    private val analyticsTracker: AnalyticsTracker
) : ViewModel() {

    private val _homeEntities = mutableListOf<FeedEntity>()
    val homeEntities: List<FeedEntity> = _homeEntities

    private val _homeEntitiesIds: MutableMap<String, Int> = mutableMapOf()
    private var isLoading = false

    private val _fetchedHomeEntities = MutableLiveData<List<FeedEntity>?>()
    val fetchedHomeEntities: LiveData<List<FeedEntity>?> =
        _fetchedHomeEntities // TODO : use Result here!

    private val _fetchedUserData = MutableLiveData<User?>()
    val fetchedUserData: MutableLiveData<User?> = _fetchedUserData

    fun notifyFetchedDoubtsConsumed() {
        _fetchedHomeEntities.postValue(null)
    }

    fun fetchUserDetails(userId: String) = viewModelScope.launch(Dispatchers.IO) {
        if (isLoading) return@launch

        isLoading = true

        val userDataResult = fetchUserDataUseCase.fetchUserDetails(
            request = FetchUserDataUseCase.FetchUserFeedRequest(
                user = User(
                    userId
                )
            )
        )
        if (userDataResult is FetchUserDataUseCase.UserDetailsResult.Error) {
            _fetchedUserData.postValue(null)
            isLoading = false
            return@launch
        }
        userDataResult as FetchUserDataUseCase.UserDetailsResult.Success
        _fetchedUserData.postValue(userDataResult.user)

        isLoading = false
    }

    fun fetchDoubts(userId: String, forPageOne: Boolean = false) =
        viewModelScope.launch(Dispatchers.IO) {

            if (isLoading) return@launch

            isLoading = true

            val result = fetchUserDataUseCase.fetchFeedSync(
                request = FetchUserDataUseCase.FetchUserFeedRequest(
                    user = User(
                        userId
                    ), fetchFromPage1 = forPageOne
                )
            )

            if (result is FetchUserDataUseCase.Result.ListEnded || result is FetchUserDataUseCase.Result.Error) {
                // ERROR CASE
                _fetchedHomeEntities.postValue(null)
                isLoading = false
                return@launch
            }

            result as FetchUserDataUseCase.Result.Success

            if (!forPageOne) {
                analyticsTracker.trackFeedNextPage(homeEntities.size)
            } else {
                analyticsTracker.trackFeedRefresh()
            }

            val entitiesFromServer = mutableListOf<FeedEntity>()

            result.data.forEach { doubtData ->
                // we got the data for page 2 (lets say) now check if these posts existed on page 1 and add only unique ones.
                if (!_homeEntitiesIds.contains(doubtData.id)) {
                    entitiesFromServer.add(doubtData.toHomeEntity())
                    _homeEntitiesIds[doubtData.id!!] = 1
                }
            }

            _homeEntities.addAll(entitiesFromServer)
            _fetchedHomeEntities.postValue(entitiesFromServer)
            fetchUserDataUseCase.notifyDistinctDocsFetched(
                docsFetched = homeEntities.size
//                    - /* subtract one for search entity, ideally should have counted Type = Doubt size */ 1
            )
            isLoading = false
        }


    companion object {
        class Factory constructor(
            private val fetchUserDataUseCase: FetchUserDataUseCase,
            private val analyticsTracker: AnalyticsTracker
        ) : ViewModelProvider.Factory {

            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return OtherUsersProfileViewModel(
                    fetchUserDataUseCase, analyticsTracker

                ) as T
            }
        }
    }
}