/**
 * ownCloud Android client application
 *
 * @author masensio
 * @author David A. Velasco
 * @author Juan Carlos González Cabrero
 * @author David González Verdugo
 * @author Christian Schabesberger
 * @author Juan Carlos Garrote Gascón
 * Copyright (C) 2022 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

package com.owncloud.android.presentation.ui.sharing.fragments

import android.accounts.Account
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.owncloud.android.R
import com.owncloud.android.datamodel.OCFile
import com.owncloud.android.datamodel.ThumbnailsCacheManager
import com.owncloud.android.domain.capabilities.model.CapabilityBooleanType
import com.owncloud.android.domain.sharing.shares.model.OCShare
import com.owncloud.android.domain.sharing.shares.model.ShareType
import com.owncloud.android.extensions.showErrorInSnackbar
import com.owncloud.android.presentation.UIResult
import com.owncloud.android.presentation.viewmodels.capabilities.OCCapabilityViewModel
import com.owncloud.android.presentation.viewmodels.sharing.OCShareViewModel
import com.owncloud.android.utils.DisplayUtils
import com.owncloud.android.utils.MimetypeIconUtil
import com.owncloud.android.utils.PreferenceUtils
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber
import java.util.Locale

/**
 * Fragment for sharing a file with sharees (users or groups) or creating
 * a public link.
 *
 * Activities that contain this fragment must implement the
 * [ShareFragmentListener] interface
 * to handle interaction events.
 *
 * Use the [ShareFileComposeFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ShareFileComposeFragment : Fragment() {

    /**
     * File to share, received as a parameter in construction time
     */
    private var file: OCFile? = null

    /**
     * OC account holding the file to share, received as a parameter in construction time
     */
    private var account: Account? = null

    /**
     * Reference to parent listener
     */
    private var listener: ShareFragmentListener? = null

    private val ocCapabilityViewModel: OCCapabilityViewModel by viewModel {
        parametersOf(
            account?.name
        )
    }

    private val ocShareViewModel: OCShareViewModel by viewModel {
        parametersOf(
            file?.remotePath,
            account?.name
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            file = it.getParcelable(ARG_FILE)
            account = it.getParcelable(ARG_ACCOUNT)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ocFile = file ?: throw IllegalArgumentException("File cannot be null")

        val shareWithUsersAllowed = resources.getBoolean(R.bool.share_with_users_feature)
        val shareViaLinkAllowed = resources.getBoolean(R.bool.share_via_link_feature)
        val shareWarningAllowed = resources.getBoolean(R.bool.warning_sharing_public_link)
        return ComposeView(requireContext()).apply {
            filterTouchesWhenObscured = PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(requireContext())
            setContent {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

                // Get capabilities to update some UI elements depending on them
                val capabilitiesState = ocCapabilityViewModel.capabilities.observeAsState()
                val capabilitiesUiResult = capabilitiesState.value?.peekContent()
                val capabilities = capabilitiesUiResult?.getStoredData()
                val isShareApiEnabled = capabilities?.filesSharingApiEnabled == CapabilityBooleanType.TRUE
                val isPublicShareEnabled = capabilities?.filesSharingPublicEnabled == CapabilityBooleanType.TRUE
                val isMultiplePublicSharingEnabled = capabilities?.filesSharingPublicMultiple?.isTrue ?: false

                // Get shares to update some UI elements depending on them
                val sharesState = ocShareViewModel.shares.observeAsState()
                val sharesUiResult = sharesState.value?.peekContent()
                val shares = sharesUiResult?.getStoredData()
                val privateShares = shares?.filter { share ->
                    share.shareType == ShareType.USER ||
                            share.shareType == ShareType.GROUP ||
                            share.shareType == ShareType.FEDERATED
                }?.sortedBy { it.sharedWithDisplayName } ?: emptyList()
                val publicShares = shares?.filter { share ->
                    share.shareType == ShareType.PUBLIC_LINK
                }?.sortedBy { it.name } ?: emptyList()

                when (capabilitiesUiResult) {
                    is UIResult.Success -> {
                        listener?.dismissLoading()
                    }
                    is UIResult.Error -> {
                        capabilitiesState.value?.getContentIfNotHandled()?.let {
                            showErrorInSnackbar(R.string.get_capabilities_error, capabilitiesUiResult.error)
                        }
                        listener?.dismissLoading()
                    }
                    is UIResult.Loading -> {
                        listener?.showLoading()
                    }
                    else -> {
                        // To avoid non-exhaustive when warning
                    }
                }

                when (sharesUiResult) {
                    is UIResult.Success -> {
                        listener?.dismissLoading()
                    }
                    is UIResult.Error -> {
                        sharesState.value?.getContentIfNotHandled()?.let {
                            showErrorInSnackbar(R.string.get_shares_error, sharesUiResult.error)
                        }
                        listener?.dismissLoading()
                    }
                    is UIResult.Loading -> {
                        listener?.showLoading()
                    }
                    else -> {
                        // To avoid non-exhaustive when warning
                    }
                }

                LazyColumn {
                    item { SharedFileRow(ocFile) }

                    // Hide share with users section if it is not enabled or if share API is not enabled
                    if (shareWithUsersAllowed && isShareApiEnabled) {
                        item {
                            SectionHeader(
                                title = stringResource(id = R.string.share_with_user_section_title),
                                showAddButton = true,
                                onClickAddButton = {
                                    // Show Search Fragment
                                    listener?.showSearchUsersAndGroups()
                                }
                            )
                        }
                        if (privateShares.isNullOrEmpty()) {
                            item { EmptyListText(text = stringResource(id = R.string.share_no_users)) }
                        } else {
                            items(privateShares) { share ->
                                ShareUserItem(
                                    share = share,
                                    unshare = {
                                        // Unshare
                                        Timber.d("Removing private share with ${share.sharedWithDisplayName}")
                                        listener?.showRemoveShare(share)
                                    },
                                    edit = {
                                        // Move to fragment to edit share
                                        Timber.d("Editing ${share.sharedWithDisplayName}")
                                        listener?.showEditPrivateShare(share)
                                    }
                                )
                                Divider(color = colorResource(id = R.color.list_divider_background))
                            }
                        }
                    }

                    // Hide share via link section if it is not enabled or if share API or public share are not enabled
                    if (shareViaLinkAllowed && isShareApiEnabled && isPublicShareEnabled) {
                        item {
                            // Show or hide button for adding a new public share depending on the capabilities and the server version
                            SectionHeader(
                                title = stringResource(id = R.string.share_via_link_section_title),
                                showAddButton = isMultiplePublicSharingEnabled || (!isMultiplePublicSharingEnabled && publicShares.isNullOrEmpty()),
                                onClickAddButton = {
                                    // Show Add Public Link Fragment
                                    listener?.showAddPublicShare(availableDefaultPublicName(publicShares))
                                }
                            )
                        }
                        // Hide warning about public links if not enabled
                        if (shareWarningAllowed) {
                            item { WarningText() }
                        }
                        if (publicShares.isNullOrEmpty()) {
                            item { EmptyListText(text = stringResource(id = R.string.share_no_public_links)) }
                        } else {
                            items(publicShares) { share ->
                                SharePublicLinkItem(
                                    share = share,
                                    copyOrSend = {
                                        // Get link from the server and show ShareLinkToDialog
                                        listener?.copyOrSendPublicLink(share)
                                    },
                                    remove = {
                                        // Remove public link from server
                                        listener?.showRemoveShare(share)
                                    },
                                    edit = {
                                        listener?.showEditPublicShare(share)
                                    }
                                )
                                Divider(color = colorResource(id = R.color.list_divider_background))
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as ShareFragmentListener?
        } catch (e: ClassCastException) {
            throw ClassCastException(activity.toString() + " must implement OnShareFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.share_dialog_title)
    }

    /**
     * Array with numbers already set in public link names
     * Inspect public links for default names already used
     * better not suggesting a name than crashing
     * Sort used numbers in ascending order
     * Search for lowest unused number
     * no missing number in the list - take the next to the last one
     */
    private fun availableDefaultPublicName(publicShares: List<OCShare>?): String {
        val defaultName = getString(
            R.string.share_via_link_default_name_template,
            file?.fileName
        )
        val defaultNameNumberedRegex = QUOTE_START + defaultName + QUOTE_END + DEFAULT_NAME_REGEX_SUFFIX
        val usedNumbers = ArrayList<Int>()
        var isDefaultNameSet = false
        var number: String
        if (publicShares != null) {
            for (share in publicShares) {
                if (defaultName == share.name) {
                    isDefaultNameSet = true
                } else if (share.name?.matches(defaultNameNumberedRegex.toRegex())!!) {
                    number = share.name!!.replaceFirst(defaultNameNumberedRegex.toRegex(), "$1")
                    try {
                        usedNumbers.add(Integer.parseInt(number))
                    } catch (e: Exception) {
                        Timber.e(e, "Wrong capture of number in share named ${share.name}")
                        return ""
                    }
                }
            }
        }

        if (!isDefaultNameSet) {
            return defaultName
        }
        usedNumbers.sort()
        var chosenNumber = UNUSED_NUMBER
        if (usedNumbers.firstOrNull() != USED_NUMBER_SECOND) {
            chosenNumber = USED_NUMBER_SECOND
        } else {
            for (i in 0 until usedNumbers.size - 1) {
                val current = usedNumbers[i]
                val next = usedNumbers[i + 1]
                if (next - current > 1) {
                    chosenNumber = current + 1
                    break
                }
            }
            if (chosenNumber < 0) {
                chosenNumber = usedNumbers[usedNumbers.size - 1] + 1
            }
        }

        return defaultName + String.format(
            Locale.getDefault(),
            DEFAULT_NAME_SUFFIX, chosenNumber
        )
    }

    @Composable
    private fun SharedFileRow(file: OCFile) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(dimensionResource(id = R.dimen.standard_padding))
        ) {
            val thumbnail = ThumbnailsCacheManager.getBitmapFromDiskCache(file.remoteId.toString())
            if (file.isImage && thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(dimensionResource(id = R.dimen.file_icon_size))
                )
            } else {
                Image(
                    painter = painterResource(id = MimetypeIconUtil.getFileTypeIconId(file.mimetype, file.fileName)),
                    contentDescription = null,
                    modifier = Modifier
                        .size(dimensionResource(id = R.dimen.file_icon_size))
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = file.fileName!!,
                    fontSize = 16.sp,
                    color = colorResource(id = R.color.black),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = dimensionResource(id = R.dimen.standard_half_margin))
                )
                if (!file.isFolder) {
                    Text(
                        text = DisplayUtils.bytesToHumanReadable(file.fileLength, activity),
                        fontSize = 12.sp,
                        color = colorResource(id = R.color.half_black)
                    )
                }
            }
            if (!file.privateLink.isNullOrEmpty()) {
                IconButton(
                    onClick = { listener?.copyOrSendPrivateLink(file) },
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = dimensionResource(id = R.dimen.standard_half_padding))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.copy_link),
                        contentDescription = null,
                        tint = colorResource(id = R.color.half_black)
                    )
                }
            }
        }
    }

    @Composable
    private fun SectionHeader(title: String, showAddButton: Boolean, onClickAddButton: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(color = colorResource(id = R.color.actionbar_start_color))
        ) {
            Text(
                text = title.uppercase(),
                modifier = Modifier.padding(start = dimensionResource(id = R.dimen.standard_half_padding)),
                color = colorResource(id = R.color.white),
                fontWeight = FontWeight.Bold
            )
            if (showAddButton) {
                IconButton(
                    onClick = onClickAddButton
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = null,
                        tint = colorResource(id = R.color.white)
                    )
                }
            }
        }
    }

    @Composable
    private fun EmptyListText(text: String) {
        Text(
            text = text,
            fontSize = 15.sp,
            color = colorResource(id = R.color.half_black),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = dimensionResource(id = R.dimen.standard_half_padding),
                    top = dimensionResource(id = R.dimen.standard_padding),
                    bottom = dimensionResource(id = R.dimen.standard_padding)
                )
        )
    }

    @Composable
    fun ShareUserItem(share: OCShare, unshare: () -> Unit, edit: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconId = if (share.shareType == ShareType.GROUP) R.drawable.ic_group else R.drawable.ic_user
            var name = if (share.sharedWithAdditionalInfo!!.isEmpty()) share.sharedWithDisplayName
            else share.sharedWithDisplayName + " (" + share.sharedWithAdditionalInfo + ")"
            if (share.shareType == ShareType.GROUP) name = getString(R.string.share_group_clarification, name)
            Icon(
                painter = painterResource(id = iconId),
                contentDescription = null,
                tint = colorResource(id = R.color.half_black),
                modifier = Modifier.padding(dimensionResource(id = R.dimen.standard_half_margin))
            )
            Text(
                text = name!!,
                fontSize = dimensionResource(id = R.dimen.two_line_primary_text_size).value.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(
                        top = dimensionResource(id = R.dimen.standard_half_margin),
                        bottom = dimensionResource(id = R.dimen.standard_half_margin),
                        start = dimensionResource(id = R.dimen.standard_half_margin)
                    )
                    .weight(1f)
            )
            IconButton(onClick = unshare) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_delete_grey),
                    contentDescription = null,
                    tint = colorResource(id = R.color.half_black),
                    modifier = Modifier
                        .size(36.dp)
                        .padding(dimensionResource(id = R.dimen.standard_half_padding))
                )
            }
            IconButton(onClick = edit) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lead_pencil_grey),
                    contentDescription = null,
                    tint = colorResource(id = R.color.half_black)
                )
            }
        }
    }

    @Composable
    private fun WarningText() {
        Text(
            text = stringResource(id = R.string.share_warning_about_forwarding_public_links),
            fontSize = 15.sp,
            color = colorResource(id = R.color.half_black),
            modifier = Modifier
                .fillMaxWidth()
                .background(color = colorResource(id = R.color.warning_background_color))
                .padding(
                    start = dimensionResource(id = R.dimen.standard_half_padding),
                    top = dimensionResource(id = R.dimen.standard_padding),
                    bottom = dimensionResource(id = R.dimen.standard_padding)
                )
        )
    }

    @Composable
    fun SharePublicLinkItem(share: OCShare, copyOrSend: () -> Unit, remove: () -> Unit, edit: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            var name = if (share.name.isNullOrEmpty()) share.token else share.name
            if (share.shareType == ShareType.GROUP) name = getString(R.string.share_group_clarification, name)
            Text(
                text = name!!,
                fontSize = dimensionResource(id = R.dimen.two_line_primary_text_size).value.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(
                        top = dimensionResource(id = R.dimen.standard_half_margin),
                        bottom = dimensionResource(id = R.dimen.standard_half_margin),
                        start = dimensionResource(id = R.dimen.standard_half_margin)
                    )
                    .weight(1f)
            )
            IconButton(onClick = copyOrSend) {
                Icon(
                    painter = painterResource(id = R.drawable.copy_link),
                    contentDescription = null,
                    tint = colorResource(id = R.color.half_black),
                    modifier = Modifier
                        .size(36.dp)
                        .padding(dimensionResource(id = R.dimen.standard_half_padding))
                )
            }
            IconButton(onClick = remove) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_action_delete_grey),
                    contentDescription = null,
                    tint = colorResource(id = R.color.half_black),
                    modifier = Modifier
                        .size(36.dp)
                        .padding(dimensionResource(id = R.dimen.standard_half_padding))
                )
            }
            IconButton(onClick = edit) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lead_pencil_grey),
                    contentDescription = null,
                    tint = colorResource(id = R.color.half_black)
                )
            }
        }
    }

    companion object {
        /**
         * The fragment initialization parameters
         */
        private const val ARG_FILE = "FILE"
        private const val ARG_ACCOUNT = "ACCOUNT"

        private const val QUOTE_START = "\\Q"
        private const val QUOTE_END = "\\E"
        private const val DEFAULT_NAME_REGEX_SUFFIX = " \\((\\d+)\\)\\z"
        // matches suffix (end of the string with \z) in the form "(X)", where X is an integer of any length;
        // also captures the number to reference it later during the match;
        // reference in https://developer.android.com/reference/java/util/regex/Pattern.html#sum

        private const val DEFAULT_NAME_SUFFIX = " (%1\$d)"

        private const val UNUSED_NUMBER = -1
        private const val USED_NUMBER_SECOND = 2

        /**
         * Public factory method to create new ShareFileFragment instances.
         *
         * @param fileToShare An [OCFile] to show in the fragment
         * @param account     An ownCloud account
         * @return A new instance of fragment ShareFileFragment.
         */
        fun newInstance(
            fileToShare: OCFile,
            account: Account
        ): ShareFileComposeFragment {
            val args = Bundle().apply {
                putParcelable(ARG_FILE, fileToShare)
                putParcelable(ARG_ACCOUNT, account)
            }
            return ShareFileComposeFragment().apply { arguments = args }
        }
    }
}
