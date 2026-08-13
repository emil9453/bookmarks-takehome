package az.bookmarks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AddBookmarkScreen(
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: AddBookmarkViewModel = viewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    // `saved` only ever flips false to true, so this fires once and the screen leaves.
    LaunchedEffect(form.saved) {
        if (form.saved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add bookmark") },
                navigationIcon = { TextButton(onClick = onCancel) { Text("Cancel") } },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.url,
                onValueChange = viewModel::onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Link") },
                placeholder = { Text("https://") },
                singleLine = true,
                isError = form.urlError != null,
                supportingText = form.urlError?.let { message -> { Text(message) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                ),
                enabled = !form.saving,
            )

            OutlinedTextField(
                value = form.title,
                onValueChange = viewModel::onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                singleLine = true,
                isError = form.titleError != null,
                supportingText = form.titleError?.let { message -> { Text(message) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                enabled = !form.saving,
            )

            TagField(
                draft = form.tagDraft,
                tags = form.tags,
                error = form.tagsError,
                enabled = !form.saving,
                onDraftChange = viewModel::onTagDraftChange,
                onConfirm = viewModel::onTagConfirmed,
                onRemove = viewModel::onTagRemoved,
            )

            OutlinedTextField(
                value = form.notes,
                onValueChange = viewModel::onNotesChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes") },
                minLines = 3,
                isError = form.notesError != null,
                supportingText = form.notesError?.let { message -> { Text(message) } },
                enabled = !form.saving,
            )

            if (form.formError != null) {
                Text(
                    text = form.formError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                // Disabled while in flight, and the ViewModel refuses a second save anyway. Both,
                // because the button is the visible half and the guard is the correct half.
                enabled = !form.saving,
            ) {
                if (form.saving) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Text("Saving…")
                    }
                } else {
                    Text("Save")
                }
            }
        }
    }
}

/**
 * Tags typed and confirmed one at a time, shown as removable chips. The keyboard's Done action
 * confirms, so the common case never needs the button — and the button exists because a hardware
 * keyboard or a user who does not know that would otherwise be stuck.
 */
@Composable
private fun TagField(
    draft: String,
    tags: List<String>,
    error: String?,
    enabled: Boolean,
    onDraftChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tags") },
            placeholder = { Text("kotlin") },
            singleLine = true,
            isError = error != null,
            supportingText = {
                Text(error ?: "Type a tag and press Done to add it.")
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm() }),
            trailingIcon = {
                if (draft.isNotBlank()) {
                    TextButton(onClick = onConfirm) { Text("Add") }
                }
            },
            enabled = enabled,
        )

        if (tags.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(count = tags.size, key = { index -> tags[index] }) { index ->
                    val tag = tags[index]
                    InputChip(
                        selected = false,
                        onClick = { onRemove(tag) },
                        label = { Text("#$tag") },
                        trailingIcon = { Text("✕") },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}
