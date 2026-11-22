package com.ex1legend.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NativeSocialPanel(
    currentUserId: String,
    contacts: List<NativePublicProfile>,
    conversation: NativeSocialConversation?,
    messages: List<NativeSocialMessage>,
    draft: String,
    loading: Boolean,
    sending: Boolean,
    error: String?,
    notice: String?,
    onRefresh: () -> Unit,
    onSelectContact: (NativePublicProfile) -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("گفت‌وگوها", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "پیام فقط بین بازیکن‌هایی فعال است که دوطرفه همدیگر را دنبال می‌کنند.",
                    color = Color(0xFF9B91AA), fontSize = 11.sp
                )
            }
            TextButton(onClick = onRefresh, enabled = !loading) { Text("به‌روزرسانی") }
        }
        if (!error.isNullOrBlank()) Text(error, color = Color(0xFFFF9B9B), fontSize = 12.sp)
        if (!notice.isNullOrBlank()) Text(notice, color = Color(0xFF9DE3B2), fontSize = 12.sp)

        Text("مخاطبان", color = Color(0xFFD5A7FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (loading && contacts.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (contacts.isEmpty()) {
            Text(
                "مخاطبی پیدا نشد. برای شروع گفت‌وگو، باید دنبال‌کردن دوطرفه تأیید شده باشد.",
                color = Color(0xFFAAA2B8), fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().background(Color(0xFF191629), RoundedCornerShape(12.dp)).padding(14.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(contacts, key = { it.id }) { person ->
                    val selected = conversation?.otherUserId == person.id
                    Card(
                        onClick = { onSelectContact(person) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) Color(0xFF392450) else Color(0xFF191629)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            Box(
                                Modifier.size(34.dp).background(Color(0xFF50336C), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text((person.displayName ?: "EX").take(1), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(person.displayName ?: "بازیکن", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(person.username?.let { "@$it" } ?: "بازیکن EX1", color = Color(0xFFAAA2B8), fontSize = 10.sp)
                            }
                            if (selected) Text("انتخاب‌شده", color = Color(0xFFD5A7FF), fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF171426)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val contactName = contacts.firstOrNull { it.id == conversation?.otherUserId }?.displayName ?: "بازیکن"
                Text(
                    if (conversation == null) "یک بازیکن را انتخاب کن." else "گفت‌وگو با " + contactName,
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                )
                if (conversation == null) {
                    Text("برای مشاهده پیام‌ها، یکی از مخاطبان را انتخاب کن.", color = Color(0xFFAAA2B8), fontSize = 11.sp)
                } else if (messages.isEmpty()) {
                    Text("هنوز پیامی وجود ندارد. اولین پیام را بفرست.", color = Color(0xFFAAA2B8), fontSize = 11.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 230.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = if (message.senderId == currentUserId) Arrangement.End else Arrangement.Start
                            ) {
                                Text(
                                    if (message.deletedAt != null) "پیام حذف‌شده" else message.body,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .background(
                                            if (message.senderId == currentUserId) Color(0xFF56377A) else Color(0xFF29263A),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 11.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = conversation != null && !sending,
                    label = { Text("پیام...") },
                    supportingText = { Text(draft.length.toString() + "/2000") },
                    maxLines = 4
                )
                Button(
                    onClick = onSend,
                    enabled = conversation != null && draft.isNotBlank() && !sending,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7543A5))
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White)
                    else Text("ارسال پیام")
                }
            }
        }
    }
}
