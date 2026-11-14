package com.omnitv.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.omnitv.app.R
import com.omnitv.app.data.AVATAR_COUNT
import com.omnitv.app.data.ProfileStore
import com.omnitv.app.data.avatarRes
import com.omnitv.app.ui.components.*
import com.omnitv.app.ui.theme.*

@Composable
fun EditProfileScreen(
    profileId: String,
    onSaved: () -> Unit,
    onDeleted: () -> Unit
) {
    val isNew = profileId == "new"
    val existing = if (isNew) null else ProfileStore.profiles.firstOrNull { it.id == profileId }

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var avatarIndex by rememberSaveable { mutableStateOf(existing?.avatarIndex ?: 0) }
    var isKids by rememberSaveable { mutableStateOf(existing?.isKids ?: false) }
    val canDelete = !isNew && ProfileStore.profiles.size > 1
    val nameFocus = remember { FocusRequester() }
    RequestTvFocus(nameFocus)

    Row(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF0F0F14),
                    0.5f to Background,
                    1f to Background
                )
            )
            .padding(horizontal = AppSpacing.screenMargin, vertical = 32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Izquierda: vista previa grande ──────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(320.dp)
        ) {
            // Avatar hero con anillo y sombra premium
            Box(contentAlignment = Alignment.Center) {
                // Halo exterior
                Box(
                    Modifier.size(200.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(44.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                )
                AvatarView(avatarIndex = avatarIndex, size = 176.dp, ringColor = Color.White)
                if (isKids) {
                    Box(Modifier.align(Alignment.BottomCenter).offset(y = 12.dp)) {
                        KidsChip()
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                if (name.isBlank()) (if (isNew) "Nuevo perfil" else existing?.name ?: "Perfil") else name,
                style = AppType.sectionTitle,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (isNew) "Elige un nombre y un avatar" else "Editando perfil",
                style = AppType.metadata,
                color = TextMuted
            )
            if (isKids) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Accent))
                    Spacer(Modifier.width(8.dp))
                    Text("Perfil infantil activo", style = AppType.caption, color = Accent)
                }
            }
            Spacer(Modifier.height(20.dp))
            // Mini stats
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MiniStat("Avatares", "8")
                Box(Modifier.width(1.dp).height(32.dp).background(Color.White.copy(alpha = 0.08f)))
                MiniStat("Tipo", if (isKids) "Kids" else "Estándar")
            }
        }

        Spacer(Modifier.width(48.dp))

        // Divisor vertical sutil
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.06f)))

        Spacer(Modifier.width(48.dp))

        // ── Derecha: formulario ─────────────────────────────────────────
        Column(Modifier.weight(1f)) {
            Text("EDITAR PERFIL", style = AppType.overline, color = Accent)
            Spacer(Modifier.height(8.dp))
            Text(
                if (isNew) "Crear perfil" else "Editar perfil",
                style = AppType.screenTitle,
                color = TextPrimary
            )
            Spacer(Modifier.height(24.dp))

            // Nombre
            Text("NOMBRE", style = AppType.chip.copy(color = TextMuted), color = TextMuted)
            Spacer(Modifier.height(10.dp))
            NameField(
                value = name,
                onValueChange = { name = it.take(16) },
                modifier = Modifier.fillMaxWidth(),
                focusRequester = nameFocus
            )
            if (name.length >= 14) {
                Spacer(Modifier.height(6.dp))
                Text("${name.length}/16 caracteres", style = AppType.caption, color = TextTertiary)
            }

            Spacer(Modifier.height(28.dp))

            // Avatar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AVATAR", style = AppType.chip.copy(color = TextMuted), color = TextMuted)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                Spacer(Modifier.width(12.dp))
                Text("${avatarIndex + 1} / $AVATAR_COUNT", style = AppType.caption, color = TextTertiary)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (index in 0 until AVATAR_COUNT) {
                    AvatarOption(
                        resId = avatarRes(index),
                        selected = index == avatarIndex,
                        onClick = { avatarIndex = index }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Elige una cara para tu perfil", style = AppType.caption, color = TextTertiary)

            Spacer(Modifier.height(28.dp))

            // Infantil — card con borde
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(AppRadius.lg)
                    .background(SurfaceCard)
                    .border(1.dp, if (isKids) Accent.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.06f), AppRadius.lg)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                SettingRow(
                    title = "Perfil infantil",
                    subtitle = "Solo contenido TP y 7+ · Sin plataformas externas",
                    trailing = { TvToggle(isKids, onCheckedChange = { isKids = it }) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(28.dp))

            // Acciones
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    text = "Guardar",
                    iconRes = R.drawable.ic_check,
                    primary = true,
                    onClick = {
                        if (isNew) ProfileStore.add(name, avatarIndex, isKids)
                        else if (existing != null) ProfileStore.update(
                            existing.copy(
                                name = name.ifBlank { existing.name },
                                avatarIndex = avatarIndex,
                                isKids = isKids
                            )
                        )
                        onSaved()
                    }
                )
                PillButton(
                    text = "Cancelar",
                    primary = false,
                    onClick = onSaved
                )
                Spacer(Modifier.weight(1f))
                if (canDelete && existing != null) {
                    PillButton(
                        text = "Eliminar",
                        iconRes = R.drawable.ic_edit,
                        primary = false,
                        danger = true,
                        onClick = {
                            ProfileStore.delete(existing.id)
                            onDeleted()
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (isNew) "Se creará un perfil vacío con tu selección"
                else "Los cambios afectan solo a este perfil",
                style = AppType.caption,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = AppType.bodyStrong, color = TextPrimary)
        Text(label, style = AppType.caption, color = TextTertiary)
    }
}
