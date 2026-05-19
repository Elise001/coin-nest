package com.example.coin_nest.ui

import android.content.Context

internal data class ProfilePrefs(
    val nickname: String = "Coin Nest 用户",
    val avatarPresetId: String = "mori",
    val avatarImageUri: String? = null,
    val selectedRewardId: String? = null,
    val lastRewardShownDay: Int = 0
)

internal enum class RewardKind(val label: String) {
    AvatarFrame("头像框"),
    ConsoleSkin("控制台"),
    AiVoice("AI"),
    ReportCover("回顾"),
    ChartAccent("图表"),
    Title("称号")
}

internal data class RewardDefinition(
    val id: String,
    val requiredDays: Int,
    val title: String,
    val description: String,
    val kind: RewardKind
)

internal data class RewardProgress(
    val activeDays: Int,
    val currentReward: RewardDefinition?,
    val nextReward: RewardDefinition?,
    val unlockedRewards: List<RewardDefinition>,
    val progressRatio: Float
)

internal data class AvatarPreset(
    val id: String
)

internal val avatarPresets = listOf(
    AvatarPreset("mori"),
    AvatarPreset("nami"),
    AvatarPreset("yuzu"),
    AvatarPreset("luna"),
    AvatarPreset("mika"),
    AvatarPreset("aoki")
)

internal val rewardCatalog = listOf(
    RewardDefinition(
        id = "first_frame",
        requiredDays = 1,
        title = "初始头像框",
        description = "第一次开始记录，就给账户一个正式身份。",
        kind = RewardKind.AvatarFrame
    ),
    RewardDefinition(
        id = "fresh_title",
        requiredDays = 3,
        title = "刚开始认真花钱",
        description = "轻量称号，提醒自己正在进入掌控节奏。",
        kind = RewardKind.Title
    ),
    RewardDefinition(
        id = "sea_salt_skin",
        requiredDays = 7,
        title = "海盐蓝控制台",
        description = "一周后解锁更清爽的账户控制台质感。",
        kind = RewardKind.ConsoleSkin
    ),
    RewardDefinition(
        id = "mint_frame",
        requiredDays = 14,
        title = "薄荷玻璃头像框",
        description = "低调但有存在感，适合长期使用。",
        kind = RewardKind.AvatarFrame
    ),
    RewardDefinition(
        id = "soft_ai_voice",
        requiredDays = 21,
        title = "温柔 AI 洞察",
        description = "让本地 AI 的预算建议更像提醒，而不是训话。",
        kind = RewardKind.AiVoice
    ),
    RewardDefinition(
        id = "month_cover",
        requiredDays = 30,
        title = "月度回顾封面",
        description = "一个月记录后，把账本变成可回看的阶段纪念。",
        kind = RewardKind.ReportCover
    ),
    RewardDefinition(
        id = "chart_accent",
        requiredDays = 45,
        title = "分类图表强调色",
        description = "让洞察页更有个人感，但不影响数据可读性。",
        kind = RewardKind.ChartAccent
    ),
    RewardDefinition(
        id = "commute_frame",
        requiredDays = 60,
        title = "晨间通勤头像框",
        description = "为稳定记录日常小额消费的人准备。",
        kind = RewardKind.AvatarFrame
    ),
    RewardDefinition(
        id = "steady_title",
        requiredDays = 90,
        title = "稳定派账本主人",
        description = "不靠压力坚持，靠自然节奏留下记录。",
        kind = RewardKind.Title
    ),
    RewardDefinition(
        id = "half_year_card",
        requiredDays = 180,
        title = "半年账本纪念卡",
        description = "半年后生成更完整的个人消费阶段感。",
        kind = RewardKind.ReportCover
    ),
    RewardDefinition(
        id = "long_term_skin",
        requiredDays = 365,
        title = "长期主义年度主题",
        description = "一年记录的专属主题，稀有但不催促。",
        kind = RewardKind.ConsoleSkin
    )
)

internal fun buildRewardProgress(
    activeDays: Int,
    selectedRewardId: String?
): RewardProgress {
    val safeDays = activeDays.coerceAtLeast(0)
    val unlocked = rewardCatalog.filter { safeDays >= it.requiredDays }
    val current = selectedRewardId
        ?.let { id -> unlocked.firstOrNull { it.id == id } }
        ?: unlocked.lastOrNull()
    val next = rewardCatalog.firstOrNull { safeDays < it.requiredDays }
    val previousRequiredDays = unlocked.lastOrNull()?.requiredDays ?: 0
    val progressRatio = if (next == null) {
        1f
    } else {
        val span = (next.requiredDays - previousRequiredDays).coerceAtLeast(1)
        ((safeDays - previousRequiredDays).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }
    return RewardProgress(
        activeDays = safeDays,
        currentReward = current,
        nextReward = next,
        unlockedRewards = unlocked,
        progressRatio = progressRatio
    )
}

internal object ProfilePreferenceStore {
    private const val PREF_NAME = "coin_nest_profile_prefs"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_AVATAR_PRESET_ID = "avatar_preset_id"
    private const val KEY_AVATAR_IMAGE_URI = "avatar_image_uri"
    private const val KEY_SELECTED_REWARD_ID = "selected_reward_id"
    private const val KEY_LAST_REWARD_SHOWN_DAY = "last_reward_shown_day"

    fun load(context: Context): ProfilePrefs {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val storedPreset = prefs.getString(KEY_AVATAR_PRESET_ID, null)?.takeIf { it.isNotBlank() }
        val safePreset = avatarPresets.firstOrNull { it.id == storedPreset }?.id ?: "mori"
        return ProfilePrefs(
            nickname = prefs.getString(KEY_NICKNAME, null)?.takeIf { it.isNotBlank() } ?: "Coin Nest 用户",
            avatarPresetId = safePreset,
            avatarImageUri = prefs.getString(KEY_AVATAR_IMAGE_URI, null)?.takeIf { it.isNotBlank() },
            selectedRewardId = prefs.getString(KEY_SELECTED_REWARD_ID, null)?.takeIf { it.isNotBlank() },
            lastRewardShownDay = prefs.getInt(KEY_LAST_REWARD_SHOWN_DAY, 0)
        )
    }

    fun saveIdentity(
        context: Context,
        nickname: String,
        avatarPresetId: String,
        avatarImageUri: String?
    ): ProfilePrefs {
        val safeNickname = nickname.trim().take(16).ifBlank { "Coin Nest 用户" }
        val safePreset = avatarPresets.firstOrNull { it.id == avatarPresetId }?.id ?: "mori"
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_NICKNAME, safeNickname)
            .putString(KEY_AVATAR_PRESET_ID, safePreset)
            .putString(KEY_AVATAR_IMAGE_URI, avatarImageUri.orEmpty())
            .apply()
        return load(context)
    }

    fun saveSelectedReward(context: Context, rewardId: String?): ProfilePrefs {
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_SELECTED_REWARD_ID, rewardId.orEmpty())
            .apply()
        return load(context)
    }

    fun markRewardShown(context: Context, requiredDays: Int): ProfilePrefs {
        val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val previous = prefs.getInt(KEY_LAST_REWARD_SHOWN_DAY, 0)
        if (requiredDays <= previous) return load(context)
        prefs.edit().putInt(KEY_LAST_REWARD_SHOWN_DAY, requiredDays).apply()
        return load(context)
    }
}
