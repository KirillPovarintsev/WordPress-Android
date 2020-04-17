package org.wordpress.android.ui.posts

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.NonNull
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.wordpress.android.R
import org.wordpress.android.WordPress
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.login.widgets.WPBottomSheetDialogFragment
import org.wordpress.android.ui.posts.EditPostSettingsFragment.EditPostActivityHook
import org.wordpress.android.ui.posts.PrepublishingActionItemUiState.ActionType
import org.wordpress.android.ui.posts.PrepublishingScreen.HOME
import org.wordpress.android.ui.posts.PrepublishingScreenState.ActionsState
import org.wordpress.android.ui.posts.PrepublishingScreenState.TagsState
import javax.inject.Inject

class PrepublishingBottomSheetFragment : WPBottomSheetDialogFragment(),
        TagsSelectedListener,
        PrepublishingScreenClosedListener, PrepublishingActionClickedListener {
    @Inject internal lateinit var viewModelFactory: ViewModelProvider.Factory

    private lateinit var viewModel: PrepublishingViewModel
    private lateinit var editPostRepository: EditPostRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NORMAL, R.style.WordPress_PrepublishingNudges_BottomSheetDialogTheme)
        (requireNotNull(activity).application as WordPress).component().inject(this)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.post_prepublishing_bottom_sheet, container)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (activity is EditPostActivityHook) {
            editPostRepository = (activity as EditPostActivityHook).editPostRepository
        } else {
            throw RuntimeException("$activity must implement EditPostActivityHook")
        }
    }

    override fun onResume() {
        super.onResume()
        /**
         * The back button normally closes the bottom sheet so now instead of doing that it goes back to
         * the home screen with the actions and only if pressed again will it close the bottom sheet.
         */
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action != KeyEvent.ACTION_DOWN) {
                    true
                } else {
                    viewModel.onBackClicked()
                    true
                }
            } else {
                false
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViewModel(savedInstanceState)
        dialog?.setOnShowListener { dialogInterface ->
            val sheetDialog = dialogInterface as? BottomSheetDialog

            val bottomSheet = sheetDialog?.findViewById<View>(
                    com.google.android.material.R.id.design_bottom_sheet
            ) as? FrameLayout

            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun initViewModel(savedInstanceState: Bundle?) {
        viewModel = ViewModelProviders.of(this, viewModelFactory)
                .get(PrepublishingViewModel::class.java)

        viewModel.navigationTarget.observe(this, Observer { event ->
            event.getContentIfNotHandled()?.let { navigationState ->
                navigateToScreen(navigationState)
            }
        })

        viewModel.dismissBottomSheet.observe(this, Observer { event ->
            event.applyIfNotHandled { dismiss() }
        })

        val prepublishingScreenState = savedInstanceState?.getParcelable<PrepublishingScreenState>(KEY_SCREEN_STATE)
        val site = arguments?.getSerializable(SITE) as SiteModel

        viewModel.start(editPostRepository, site, prepublishingScreenState)
    }

    private fun navigateToScreen(navigationTarget: PrepublishingNavigationTarget) {
        val (fragment, tag) = when (navigationTarget.targetScreen) {
            HOME -> Pair(
                    PrepublishingActionsFragment.newInstance((navigationTarget.screenState as ActionsState)),
                    PrepublishingActionsFragment.TAG
            )
            PrepublishingScreen.PUBLISH -> TODO()
            PrepublishingScreen.VISIBILITY -> TODO()
            PrepublishingScreen.TAGS -> Pair(
                    PrepublishingTagsFragment.newInstance(
                            navigationTarget.site,
                            (navigationTarget.screenState as? TagsState)?.tags
                    ),
                    PrepublishingTagsFragment.TAG
            )
        }

        slideInFragment(fragment, tag, tag != PrepublishingActionsFragment.TAG)
    }

    private fun slideInFragment(fragment: Fragment, tag: String, slideBack: Boolean) {
        childFragmentManager.let { fragmentManager ->
            val fragmentTransaction = fragmentManager.beginTransaction()
            fragmentManager.findFragmentById(R.id.prepublishing_content_fragment)?.run {
                if (slideBack) {
                    fragmentTransaction.addToBackStack(null).setCustomAnimations(
                            R.anim.activity_slide_in_from_right, R.anim.activity_slide_out_to_left,
                            R.anim.activity_slide_in_from_left, R.anim.activity_slide_out_to_right
                    )
                } else {
                    fragmentTransaction.addToBackStack(null).setCustomAnimations(
                            R.anim.activity_slide_in_from_left, R.anim.activity_slide_out_to_right,
                            R.anim.activity_slide_in_from_right, R.anim.activity_slide_out_to_left
                    )
                }
            }
            fragmentTransaction.replace(R.id.prepublishing_content_fragment, fragment, tag)
            fragmentTransaction.commit()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        viewModel.writeToBundle(outState)
    }

    companion object {
        const val TAG = "prepublishing_bottom_sheet_fragment_tag"
        const val SITE = "prepublishing_bottom_sheet_site_model"

        @JvmStatic
        fun newInstance(@NonNull site: SiteModel) = PrepublishingBottomSheetFragment().apply {
            arguments = Bundle().apply {
                putSerializable(SITE, site)
            }
        }
    }

    override fun onTagsSelected(selectedTags: String) {
        viewModel.updateTagsStateAndSetToCurrent(selectedTags)
    }

    override fun onCloseClicked() {
        viewModel.onCloseClicked()
    }

    override fun onBackClicked() {
        viewModel.onBackClicked()
    }

    override fun onActionClicked(actionType: ActionType) {
        viewModel.onActionClicked(actionType)
    }
}
