package com.doubtless.doubtless.screens.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.doubtless.doubtless.DoubtlessApp
import com.doubtless.doubtless.analytics.AnalyticsTracker
import com.doubtless.doubtless.databinding.FragmentDashboardBinding
import com.doubtless.doubtless.screens.auth.usecases.UserManager
import com.doubtless.doubtless.screens.common.GenericFeedAdapter
import com.doubtless.doubtless.screens.doubt.DoubtData
import com.doubtless.doubtless.screens.home.entities.FeedEntity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardFragment : Fragment() {

    private lateinit var mAuth: FirebaseAuth
    private lateinit var userManager: UserManager
    private var _binding: FragmentDashboardBinding? = null
    private lateinit var viewModel: DashboardViewModel
    private lateinit var adapter: GenericFeedAdapter

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var tracker: AnalyticsTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tracker = DoubtlessApp.getInstance().getAppCompRoot().getAnalyticsTracker()
        mAuth = FirebaseAuth.getInstance()
        userManager = DoubtlessApp.getInstance().getAppCompRoot().getUserManager()

        viewModel = getViewModel()
        viewModel.fetchDoubts(forPageOne = true)

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var lastRefreshed = System.currentTimeMillis()

        val feedList = mutableListOf<FeedEntity>()
        feedList.add(FeedEntity(FeedEntity.TYPE_USER_PROFILE, null))

        if (!::adapter.isInitialized) {
            adapter = GenericFeedAdapter(genericFeedEntities = feedList,
                onLastItemReached = {
                    viewModel.fetchDoubts()
                },
                interactionListener = object : GenericFeedAdapter.InteractionListener {
                    override fun onSearchBarClicked() {
                    }


                    override fun onDoubtClicked(doubtData: DoubtData, position: Int) {

                    }

                    override fun onSignOutClicked() {
                        tracker.trackLogout()

                        CoroutineScope(Dispatchers.Main).launch {

                            val result = withContext(Dispatchers.IO) {
                                DoubtlessApp.getInstance().getAppCompRoot().getUserManager()
                                    .onUserLogoutSync()
                            }

                            if (!isAdded) return@launch

                            if (result is UserManager.Result.LoggedOut) {

                                DoubtlessApp.getInstance()
                                    .getAppCompRoot().router.moveToLoginActivity(
                                        requireActivity()
                                    )
                                requireActivity().finish()

                            } else if (result is UserManager.Result.Error) {

                                Toast.makeText(
                                    this@DashboardFragment.requireContext(),
                                    result.message,
                                    Toast.LENGTH_LONG
                                ).show() // encapsulate error ui handling

                            }
                        }
                    }

                    override fun onSubmitFeedbackClicked() {
                        tracker.trackFeedbackButtonClicked()
                        submitFeedback()
                    }
                })
        }



        binding.doubtsRecyclerView.adapter = adapter
        binding.doubtsRecyclerView.layoutManager = LinearLayoutManager(context)

        viewModel.fetchedHomeEntities.observe(viewLifecycleOwner) {
            if (it == null) return@observe
            adapter.appendDoubts(it)
            Log.i("ObserveFeed", it.toTypedArray().contentToString())
            viewModel.notifyFetchedDoubtsConsumed()
        }
    }

    private fun getViewModel(): DashboardViewModel {
        return ViewModelProvider(
            owner = this, factory = DashboardViewModel.Companion.Factory(
                fetchUserDataUseCase = DoubtlessApp.getInstance().getAppCompRoot()
                    .getFetchUserDataUseCase(),
                analyticsTracker = tracker,
                userManager = userManager
            )
        )[DashboardViewModel::class.java]
    }

    private fun submitFeedback() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_EMAIL, arrayOf("doubtless46@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Feedback by ${userManager.getCachedUserData()!!.name}")
            putExtra(Intent.EXTRA_TEXT, "Enter Feedback Here")
            selector = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
            }
        }
        startActivity(intent)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}