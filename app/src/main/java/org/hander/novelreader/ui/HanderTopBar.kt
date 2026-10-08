package org.hander.novelreader.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hander.novelreader.R
import org.hander.novelreader.ui.theme.HanderBackground
import org.hander.novelreader.ui.theme.HanderBorder
import org.hander.novelreader.ui.theme.HanderIvory
import org.hander.novelreader.ui.theme.HanderMuted
import org.hander.novelreader.ui.theme.HanderTeal

@Composable
fun HanderTopBar(online: Boolean) {
    Column(
        modifier = Modifier.background(HanderBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    horizontal = 16.dp,
                    vertical = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.hander_logo),
                contentDescription = "Hander",
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
            )

            Spacer(
                modifier = Modifier.width(12.dp)
            )

            Text(
                text = "HANDER",
                color = HanderIvory,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                fontSize = 15.sp
            )

            Spacer(
                modifier = Modifier.weight(1f)
            )

            if (!online) {
                Text(
                    text = "Offline",
                    color = HanderMuted,
                    fontSize = 12.sp
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )
            }

            Icon(
                imageVector = if (online) {
                    Icons.Outlined.Wifi
                } else {
                    Icons.Outlined.WifiOff
                },
                contentDescription = if (online) {
                    "Online"
                } else {
                    "No internet connection"
                },
                tint = HanderTeal,
                modifier = Modifier.size(20.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HanderBorder)
        )
    }
}