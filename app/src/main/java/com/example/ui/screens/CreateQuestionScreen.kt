package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.ai.AiAssistantResult
import com.example.data.model.AppCategories
import com.example.data.model.Question
import com.example.data.model.QuestionPrivacy
import com.example.ui.components.AiResultCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateQuestionScreen(
    existingQuestion: Question?,
    isSubmitting: Boolean,
    aiResult: AiAssistantResult?,
    aiLoading: Boolean,
    onBack: () -> Unit,
    onAiRefine: (String, String) -> Unit,
    onDismissAi: () -> Unit,
    onSubmit: (
        title: String,
        body: String,
        category: String,
        privacy: String,
        imageUri: Uri?,
        existingImageUrl: String?
    ) -> Unit
) {
    var title by remember(existingQuestion) { mutableStateOf(existingQuestion?.title.orEmpty()) }
    var body by remember(existingQuestion) { mutableStateOf(existingQuestion?.body.orEmpty()) }
    val categories = remember { AppCategories.items.filter { it != AppCategories.ALL } }
    var selectedCategory by remember(existingQuestion) {
        mutableStateOf(existingQuestion?.category ?: categories.first())
    }
    var selectedPrivacy by remember(existingQuestion) {
        mutableStateOf(existingQuestion?.privacy ?: QuestionPrivacy.PUBLIC.value)
    }
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
    var existingImageUrl by remember(existingQuestion) { mutableStateOf(existingQuestion?.imageUrl) }

    // Zero-permission Android Photo Picker (Google Play compliant)
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedImageUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (existingQuestion == null) "طرح سؤال جديد" else "تعديل السؤال",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("create_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان السؤال") },
                placeholder = { Text("اكتب سؤالاً واضحاً ومختصراً...") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_title_input")
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("التفاصيل والسياق") },
                placeholder = { Text("اشرح سؤالك بالتفصيل لمساعدة الآخرين على إعطائك أفضل إجابة...") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_body_input")
            )

            OutlinedButton(
                onClick = { onAiRefine(title, body) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_ai_refine_button")
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("اقتراح صياغة أفضل بالذكاء الاصطناعي")
            }

            AiResultCard(
                result = aiResult,
                isLoading = aiLoading,
                onDismiss = onDismissAi,
                onApplySuggestion = { suggestedTitle ->
                    title = suggestedTitle
                    onDismissAi()
                }
            )

            Text("التصنيف", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            Text("إعدادات الخصوصية", style = MaterialTheme.typography.titleSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuestionPrivacy.entries.forEach { priv ->
                    FilterChip(
                        selected = selectedPrivacy == priv.value,
                        onClick = { selectedPrivacy = priv.value },
                        label = { Text(priv.labelAr) }
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.testTag("create_pick_image_button")
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إرفاق صورة")
                }

                if (pickedImageUri != null || !existingImageUrl.isNullOrBlank()) {
                    IconButton(onClick = {
                        pickedImageUri = null
                        existingImageUrl = null
                    }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "إزالة الصورة",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            val previewModel: Any? = pickedImageUri ?: existingImageUrl
            if (previewModel != null) {
                AsyncImage(
                    model = previewModel,
                    contentDescription = "معاينة الصورة المرفقة",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    onSubmit(title, body, selectedCategory, selectedPrivacy, pickedImageUri, existingImageUrl)
                },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("create_submit_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text(if (existingQuestion == null) "نشر السؤال" else "حفظ التعديلات")
                }
            }
        }
    }
}
