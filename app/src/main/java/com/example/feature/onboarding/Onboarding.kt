package com.example.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.brand.FarLogo
import kotlinx.coroutines.launch

/**
 * Three pages. No permission dialog appears until the user taps a button on page 3.
 * onFullAccess   -> request READ_MEDIA_* (+ VISUAL_USER_SELECTED on 34+) / READ_EXTERNAL_STORAGE on 30-32
 * onSelectedOnly -> launch Photo Picker / Visual User Selected access flow
 * onSkip         -> enter the app with "No access" state (friendly empty Library)
 */
@Composable
fun OnboardingScreen(
    onFullAccess: () -> Unit,
    onSelectedOnly: () -> Unit,
    onSkip: () -> Unit,
) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (page) {
                        0 -> FarLogo(size = 136.dp)
                        1 -> Box(
                            Modifier
                                .size(112.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.PhotoCamera,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        else -> Box(
                            Modifier
                                .size(112.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.LockOpen,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        text = when (page) {
                            0 -> stringResource(R.string.onboarding_title_1)
                            1 -> stringResource(R.string.onboarding_title_2)
                            else -> stringResource(R.string.onboarding_title_3)
                        },
                        modifier = Modifier.padding(top = 28.dp),
                        style = MaterialTheme.typography.displayLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = when (page) {
                            0 -> stringResource(R.string.onboarding_body_1)
                            1 -> stringResource(R.string.onboarding_body_2)
                            else -> stringResource(R.string.onboarding_body_3)
                        },
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .size(if (i == pager.currentPage) 10.dp else 8.dp)
                            .background(
                                if (i == pager.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            )
                    )
                }
            }
            if (pager.currentPage < 2) {
                Button(
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_continue_button"),
                ) { Text(stringResource(R.string.onboarding_continue)) }
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.testTag("onboarding_skip_button"),
                ) { Text(stringResource(R.string.onboarding_skip)) }
            } else {
                Button(
                    onClick = onFullAccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_full_access_button"),
                ) { Text(stringResource(R.string.onboarding_allow_full)) }
                OutlinedButton(
                    onClick = onSelectedOnly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("onboarding_selected_only_button"),
                ) { Text(stringResource(R.string.onboarding_choose_selected)) }
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.testTag("onboarding_skip_now_button"),
                ) { Text(stringResource(R.string.onboarding_skip_now)) }
            }
        }
    }
}
