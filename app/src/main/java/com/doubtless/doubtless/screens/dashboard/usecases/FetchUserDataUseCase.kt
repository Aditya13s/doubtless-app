package com.doubtless.doubtless.screens.dashboard.usecases

import android.util.Log
import androidx.annotation.WorkerThread
import com.doubtless.doubtless.DoubtlessApp
import com.doubtless.doubtless.constants.FirestoreCollection
import com.doubtless.doubtless.screens.auth.usecases.UserManager
import com.doubtless.doubtless.screens.doubt.DoubtData
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FetchUserDataUseCase constructor(
    private val fetchUserFeedByDateUseCase: FetchUserFeedByDateUseCase,
    private val firestore: FirebaseFirestore
) {

    private val userManager = DoubtlessApp.getInstance().getAppCompRoot().getUserManager()


    data class FetchUserFeedRequest(
        val user: com.doubtless.doubtless.screens.auth.User,
        val pageSize: Int = 10,
        val fetchFromPage1: Boolean = false
    )

    sealed class Result() {
        class Success(val data: List<DoubtData>) : Result()
        class ListEnded : Result()
        class Error(val message: String) : Result()
    }

    private var lastDoubtData: DoubtData? = null
    private var collectionCount = -1L
    private var docFetched = 0L

    @WorkerThread
    suspend fun fetchFeedSync(request: FetchUserFeedRequest): Result = withContext(Dispatchers.IO) {

        // if this is a refresh call then reset all the params,
        // though for now, we don't reset collection count.
        if (request.fetchFromPage1) {
            docFetched = 0
            lastDoubtData = null
        }
//
//        // predetermine the count of total doubts to paginate accordingly.
        val fetchCountResult = fetchCollectionCountIfNotDoneAlready()
        if (fetchCountResult != null) return@withContext fetchCountResult
//
//        // doc fetched should be less than total count in order to make a new call.
        if (collectionCount <= docFetched) return@withContext Result.ListEnded()
//
//        // if total size = 33 and docFetched = 30, then request only 3 more,
//        // else request page size.
        val size: Int =
            if (collectionCount - docFetched < request.pageSize) (collectionCount - docFetched).toInt()
            else request.pageSize
        val userFeedByDateJob = async {
            fetchUserFeedByDateUseCase.getFeedData(
                request.copy(pageSize = size / 2), userManager
            ) // ratio
        }
        val resultDate = userFeedByDateJob.await()
        if (resultDate is FetchUserFeedByDateUseCase.Result.Error) {
            return@withContext Result.Error(resultDate.message)
        }
        resultDate as FetchUserFeedByDateUseCase.Result.Success

        return@withContext Result.Success(resultDate.data)
    }

    private fun fetchCollectionCountIfNotDoneAlready(): Result? {
        if (collectionCount == -1L) {
            val latch = CountDownLatch(1)

            firestore.collection(FirestoreCollection.AllDoubts)
                .whereEqualTo("userId", userManager.getCachedUserData()?.id).count()
                .get(AggregateSource.SERVER).addOnSuccessListener {
                    collectionCount = it.count
                    latch.countDown()
                }.addOnFailureListener {
                    latch.countDown()
                }

            latch.await(10, TimeUnit.SECONDS)
            Log.d("doubts count :", "$collectionCount")

            if (collectionCount == -1L) return Result.Error("some error occurred!")
        }

        return null
    }


}

class FetchUserFeedByDateUseCase constructor(
    private val firestore: FirebaseFirestore
) {

    sealed class Result {
        class Success(val data: List<DoubtData>) : Result()
        class Error(val message: String) : Result()
    }

    private var lastDoubtData: DoubtData? = null

    suspend fun getFeedData(
        request: FetchUserDataUseCase.FetchUserFeedRequest, userManager: UserManager
    ): Result = withContext(Dispatchers.IO) {

        try {
            var query = firestore.collection(FirestoreCollection.AllDoubts).whereEqualTo(
                "userId", userManager.getCachedUserData()!!.id
            ).orderBy(FieldPath.of("date"), Query.Direction.DESCENDING)

            if (lastDoubtData != null && request.fetchFromPage1 == false) {
                query = query.startAfter(lastDoubtData!!.date)
            }

            // if total size = 33 and docFetched = 30, then request only 3 more,
            // else request page size.
            query.limit(request.pageSize.toLong())

            val result = query.get().await()
            val doubtDataList = getDoubtDataList(result)

            return@withContext Result.Success(doubtDataList)

        } catch (e: Exception) {
            return@withContext Result.Error(e.message ?: "some error occurred!")
        }
    }

    @kotlin.jvm.Throws(Exception::class)
    private fun getDoubtDataList(result: QuerySnapshot?): List<DoubtData> {

        val doubtDataList = mutableListOf<DoubtData>()

        result!!.documents.forEach {
            val doubtData = DoubtData.parse(it) ?: return@forEach
            doubtDataList.add(doubtData)
        }

        return doubtDataList
    }


}