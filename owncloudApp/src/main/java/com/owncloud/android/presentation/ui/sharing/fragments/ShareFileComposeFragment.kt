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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
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
import com.owncloud.android.domain.sharing.shares.model.OCShare
import com.owncloud.android.utils.DisplayUtils
import com.owncloud.android.utils.MimetypeIconUtil
import com.owncloud.android.utils.PreferenceUtils
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
 * Use the [ShareFileFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ShareFileComposeFragment: Fragment() {

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

    /**
     * List of public links bound to the file
     */
    private var publicLinks: List<OCShare> = listOf()

    /**
     * Array with numbers already set in public link names
     * Inspect public links for default names already used
     * better not suggesting a name than crashing
     * Sort used numbers in ascending order
     * Search for lowest unused number
     * no missing number in the list - take the next to the last one
     */
    private val availableDefaultPublicName: String
        get() {
            val defaultName = getString(
                R.string.share_via_link_default_name_template,
                file?.fileName
            )
            val defaultNameNumberedRegex = QUOTE_START + defaultName + QUOTE_END + DEFAULT_NAME_REGEX_SUFFIX
            val usedNumbers = ArrayList<Int>()
            var isDefaultNameSet = false
            var number: String
            for (share in publicLinks) {
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
        val shareWithUsersAllowed = resources.getBoolean(R.bool.share_with_users_feature)
        val shareViaLinkAllowed = resources.getBoolean(R.bool.share_via_link_feature)
        val shareWarningAllowed = resources.getBoolean(R.bool.warning_sharing_public_link)
        return ComposeView(requireContext()).apply {
            filterTouchesWhenObscured = PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(requireContext())
            setContent {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = dimensionResource(id = R.dimen.standard_padding))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(dimensionResource(id = R.dimen.standard_padding))
                    ) {
                        val thumbnail = ThumbnailsCacheManager.getBitmapFromDiskCache(file?.remoteId.toString())
                        if (file!!.isImage && thumbnail != null) {
                            Image(
                                bitmap = thumbnail.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(dimensionResource(id = R.dimen.file_icon_size))
                                    .fillMaxSize()
                            )
                        } else {
                            Image(
                                painter = painterResource(id = MimetypeIconUtil.getFileTypeIconId(file?.mimetype, file?.fileName)),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(dimensionResource(id = R.dimen.file_icon_size))
                                    .fillMaxSize()
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(
                                text = file?.fileName!!,
                                fontSize = 16.sp,
                                color = colorResource(id = R.color.black),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(end = dimensionResource(id = R.dimen.standard_half_margin))
                            )
                            if (!file!!.isFolder) {
                                Text(
                                    text = DisplayUtils.bytesToHumanReadable(file!!.fileLength, activity),
                                    fontSize = 12.sp,
                                    color = colorResource(id = R.color.half_black)
                                )
                            }
                        }
                        if (!file?.privateLink.isNullOrEmpty()) {
                            IconButton(
                                onClick = { listener?.copyOrSendPrivateLink(file!!) },
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
                    // Hide share with users section if it is not enabled
                    if (shareWithUsersAllowed) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(color = colorResource(id = R.color.actionbar_start_color))
                                .padding(
                                    end = dimensionResource(id = R.dimen.standard_half_margin),
                                    top = dimensionResource(id = R.dimen.standard_quarter_margin),
                                    bottom = dimensionResource(id = R.dimen.standard_quarter_margin)
                                )
                        ) {
                            Text(
                                text = stringResource(id = R.string.share_with_user_section_title).uppercase(),
                                modifier = Modifier.padding(start = dimensionResource(id = R.dimen.standard_half_padding)),
                                color = colorResource(id = R.color.white),
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    // Show Search Fragment
                                    listener?.showSearchUsersAndGroups()
                                          },
                                modifier = Modifier.then(Modifier.size(32.dp))
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_add),
                                    contentDescription = null,
                                    tint = colorResource(id = R.color.white)
                                )
                            }
                        }
                        Text(
                            text = stringResource(id = R.string.share_no_users),
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
                    // Hide share via link section if it is not enabled
                    if (shareViaLinkAllowed) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(color = colorResource(id = R.color.actionbar_start_color))
                                .padding(
                                    end = dimensionResource(id = R.dimen.standard_half_margin),
                                    top = dimensionResource(id = R.dimen.standard_quarter_margin),
                                    bottom = dimensionResource(id = R.dimen.standard_quarter_margin)
                                )
                        ) {
                            Text(
                                text = stringResource(id = R.string.share_via_link_section_title).uppercase(),
                                modifier = Modifier.padding(start = dimensionResource(id = R.dimen.standard_half_padding)),
                                color = colorResource(id = R.color.white),
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    // Show Add Public Link Fragment
                                    listener?.showAddPublicShare(availableDefaultPublicName)
                                          },
                                modifier = Modifier.then(Modifier.size(32.dp))
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_add),
                                    contentDescription = null,
                                    tint = colorResource(id = R.color.white)
                                )
                            }
                        }
                        // Hide warning about public links if not enabled
                        if (shareWarningAllowed) {
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
                        Text(
                            text = stringResource(id = R.string.share_no_public_links),
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
