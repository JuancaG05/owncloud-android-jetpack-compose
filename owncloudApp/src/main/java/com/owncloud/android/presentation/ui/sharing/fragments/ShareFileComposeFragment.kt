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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import com.owncloud.android.R
import com.owncloud.android.datamodel.OCFile
import com.owncloud.android.datamodel.ThumbnailsCacheManager
import com.owncloud.android.utils.DisplayUtils
import com.owncloud.android.utils.MimetypeIconUtil

class ShareFileComposeFragment: Fragment() {

    private var file: OCFile? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            file = it.getParcelable(ARG_FILE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
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
                            modifier = Modifier.weight(1f).padding(start = 12.dp)
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
                                    color = colorResource(id = R.color.half_black),
                                )
                            }
                        }
                        Image(
                            painter = painterResource(id = R.drawable.copy_link),
                            contentDescription = null,
                            modifier = Modifier
                                .size(28.dp)
                                .padding(end = dimensionResource(id = R.dimen.standard_half_padding))
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val ARG_FILE = "FILE"

        fun newInstance(
            fileToShare: OCFile
        ): ShareFileComposeFragment {
            val args = Bundle().apply {
                putParcelable(ARG_FILE, fileToShare)
            }
            return ShareFileComposeFragment().apply { arguments = args }
        }
    }
}