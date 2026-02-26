package com.mllm.chat.ui.settings

import com.mllm.chat.data.model.Provider
import com.mllm.chat.data.remote.ApiResult
import com.mllm.chat.data.repository.ChatRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SettingsViewModel] covering provider management, dialog state, field
 * validation, and repository interaction.
 *
 * IMPORTANT: [UnconfinedTestDispatcher] is set as the Main dispatcher so that
 * [androidx.lifecycle.ViewModel.viewModelScope] coroutines run eagerly inline.
 * Because `init { loadProviders() }` fires immediately on ViewModel construction,
 * all repository stubs that `loadProviders` depends on must be set up BEFORE calling
 * [createViewModel].  Stubs set AFTER creation start a fresh invocation count and are
 * NOT affected by the init call.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var repository: ChatRepository

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        // Set default stubs that loadProviders() needs during ViewModel construction.
        stubDefaultLoadProviders()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Stubs the repository calls made by loadProviders() with safe defaults (empty state).
     * Call this before [createViewModel] to configure what init sees.
     */
    private fun stubDefaultLoadProviders(
        providers: List<Provider> = emptyList(),
        activeProvider: Provider? = null
    ) {
        coEvery { repository.getProviders() } returns providers
        coEvery { repository.getActiveProvider() } returns activeProvider
        every { repository.getWebSearchEnabled() } returns false
        every { repository.getWebSearchApiKey() } returns ""
        every { repository.getWebSearchProvider() } returns "brave"
    }

    /** Creates the ViewModel.  init immediately calls loadProviders() via UnconfinedTestDispatcher. */
    private fun createViewModel(): SettingsViewModel = SettingsViewModel(repository)

    private fun makeProvider(
        id: String = "p-id",
        name: String = "Test Provider",
        baseUrl: String = "https://api.openai.com/v1",
        apiKey: String = "sk-key",
        selectedModel: String = "gpt-4",
        systemPrompt: String = "",
        temperature: Float? = null,
        maxTokens: Int? = null,
        availableModels: List<String> = emptyList()
    ) = Provider(
        id = id,
        name = name,
        baseUrl = baseUrl,
        apiKey = apiKey,
        selectedModel = selectedModel,
        systemPrompt = systemPrompt,
        temperature = temperature,
        maxTokens = maxTokens,
        availableModels = availableModels
    )

    /**
     * Stubs all repository calls required for a successful saveProvider() flow.
     * [getProviders] is called twice inside saveProvider (allProviders check + final reload),
     * [getActiveProvider] is called twice (hasNoActiveProvider check + final reload).
     * Both calls receive the same value via a simple `returns`.
     *
     * @param allProviders  what the DAO returns after saving (affects auto-activation logic)
     * @param activeProvider null → hasNoActiveProvider=true (triggers auto-activate on its own)
     * @param expectActivate when true, stubs setActiveProvider so the test doesn't blow up
     */
    private fun stubSaveProvider(
        allProviders: List<Provider> = emptyList(),
        activeProvider: Provider? = null,
        expectActivate: Boolean = true
    ) {
        coJustRun { repository.saveProvider(any()) }
        coEvery { repository.getProviders() } returns allProviders
        coEvery { repository.getActiveProvider() } returns activeProvider
        if (expectActivate) {
            coJustRun { repository.setActiveProvider(any()) }
        }
    }

    // ── init / loadProviders ─────────────────────────────────────────────────

    @Test
    fun `init populates providers list from repository`() = runTest {
        val providers = listOf(
            makeProvider(id = "a", name = "Alpha"),
            makeProvider(id = "b", name = "Beta")
        )
        stubDefaultLoadProviders(providers = providers, activeProvider = providers[0])
        val vm = createViewModel()

        assertEquals(2, vm.uiState.value.providers.size)
        assertEquals("Alpha", vm.uiState.value.providers[0].name)
        assertEquals("Beta", vm.uiState.value.providers[1].name)
    }

    @Test
    fun `init sets activeProviderId from repository`() = runTest {
        val active = makeProvider(id = "active-id")
        stubDefaultLoadProviders(providers = listOf(active), activeProvider = active)
        val vm = createViewModel()

        assertEquals("active-id", vm.uiState.value.activeProviderId)
    }

    @Test
    fun `init sets activeProviderId to null when no active provider`() = runTest {
        stubDefaultLoadProviders(providers = emptyList(), activeProvider = null)
        val vm = createViewModel()

        assertNull(vm.uiState.value.activeProviderId)
    }

    @Test
    fun `init loads web search settings from repository`() = runTest {
        every { repository.getWebSearchEnabled() } returns true
        every { repository.getWebSearchApiKey() } returns "brave-key-123"
        every { repository.getWebSearchProvider() } returns "brave"
        val vm = createViewModel()

        assertTrue(vm.uiState.value.webSearchEnabled)
        assertEquals("brave-key-123", vm.uiState.value.webSearchApiKey)
        assertEquals("brave", vm.uiState.value.webSearchProvider)
    }

    // ── openAddProviderDialog ────────────────────────────────────────────────

    @Test
    fun `openAddProviderDialog shows dialog`() = runTest {
        val vm = createViewModel()
        vm.openAddProviderDialog()
        assertTrue(vm.uiState.value.showProviderDialog)
    }

    @Test
    fun `openAddProviderDialog sets editingProviderId to null`() = runTest {
        val vm = createViewModel()
        vm.openAddProviderDialog()
        assertNull(vm.uiState.value.editingProviderId)
    }

    @Test
    fun `openAddProviderDialog clears all dialog fields to defaults`() = runTest {
        val vm = createViewModel()
        vm.openAddProviderDialog()

        with(vm.uiState.value) {
            assertEquals("", dialogName)
            assertEquals("", dialogBaseUrl)
            assertEquals("", dialogApiKey)
            assertEquals("", dialogModel)
            assertEquals("", dialogSystemPrompt)
            assertEquals(0.7f, dialogTemperature)
            assertFalse(dialogUseTemperature)
            assertEquals("", dialogMaxTokens)
            assertTrue(dialogAvailableModels.isEmpty())
            assertNull(testResult)
            assertNull(saveProviderError)
        }
    }

    // ── openEditProviderDialog ───────────────────────────────────────────────

    @Test
    fun `openEditProviderDialog shows dialog`() = runTest {
        val vm = createViewModel()
        vm.openEditProviderDialog(makeProvider())
        assertTrue(vm.uiState.value.showProviderDialog)
    }

    @Test
    fun `openEditProviderDialog sets editingProviderId from provider`() = runTest {
        val vm = createViewModel()
        vm.openEditProviderDialog(makeProvider(id = "edit-target"))
        assertEquals("edit-target", vm.uiState.value.editingProviderId)
    }

    @Test
    fun `openEditProviderDialog populates all dialog fields from provider`() = runTest {
        val provider = makeProvider(
            id = "edit-id",
            name = "Editable Provider",
            baseUrl = "https://openrouter.ai/api/v1",
            apiKey = "sk-or-v1-key",
            selectedModel = "llama3",
            systemPrompt = "Be concise.",
            temperature = 0.5f,
            maxTokens = 512,
            availableModels = listOf("llama3", "mistral")
        )
        val vm = createViewModel()
        vm.openEditProviderDialog(provider)

        with(vm.uiState.value) {
            assertEquals("Editable Provider", dialogName)
            assertEquals("https://openrouter.ai/api/v1", dialogBaseUrl)
            assertEquals("sk-or-v1-key", dialogApiKey)
            assertEquals("llama3", dialogModel)
            assertEquals("Be concise.", dialogSystemPrompt)
            assertEquals(0.5f, dialogTemperature)
            assertTrue(dialogUseTemperature)
            assertEquals("512", dialogMaxTokens)
            assertEquals(listOf("llama3", "mistral"), dialogAvailableModels)
        }
    }

    @Test
    fun `openEditProviderDialog uses first available model when selectedModel is blank`() = runTest {
        val provider = makeProvider(
            selectedModel = "",
            availableModels = listOf("gpt-4", "gpt-3.5-turbo")
        )
        val vm = createViewModel()
        vm.openEditProviderDialog(provider)
        assertEquals("gpt-4", vm.uiState.value.dialogModel)
    }

    @Test
    fun `openEditProviderDialog uses gpt-4 fallback when selectedModel blank and no available models`() = runTest {
        val provider = makeProvider(selectedModel = "", availableModels = emptyList())
        val vm = createViewModel()
        vm.openEditProviderDialog(provider)
        assertEquals("gpt-4", vm.uiState.value.dialogModel)
    }

    @Test
    fun `openEditProviderDialog sets dialogUseTemperature false when temperature is null`() = runTest {
        val provider = makeProvider(temperature = null)
        val vm = createViewModel()
        vm.openEditProviderDialog(provider)
        assertFalse(vm.uiState.value.dialogUseTemperature)
    }

    @Test
    fun `openEditProviderDialog sets maxTokens to empty string when null`() = runTest {
        val provider = makeProvider(maxTokens = null)
        val vm = createViewModel()
        vm.openEditProviderDialog(provider)
        assertEquals("", vm.uiState.value.dialogMaxTokens)
    }

    @Test
    fun `openEditProviderDialog clears testResult and saveProviderError`() = runTest {
        val vm = createViewModel()
        vm.openEditProviderDialog(makeProvider())
        assertNull(vm.uiState.value.testResult)
        assertNull(vm.uiState.value.saveProviderError)
    }

    // ── dismissProviderDialog ────────────────────────────────────────────────

    @Test
    fun `dismissProviderDialog hides the dialog`() = runTest {
        val vm = createViewModel()
        vm.openAddProviderDialog()
        assertTrue(vm.uiState.value.showProviderDialog)

        vm.dismissProviderDialog()
        assertFalse(vm.uiState.value.showProviderDialog)
    }

    // ── field update methods ─────────────────────────────────────────────────

    @Test
    fun `updateDialogName updates dialogName`() = runTest {
        val vm = createViewModel()
        vm.updateDialogName("My Custom Name")
        assertEquals("My Custom Name", vm.uiState.value.dialogName)
    }

    @Test
    fun `updateDialogBaseUrl updates dialogBaseUrl and clears testResult`() = runTest {
        val vm = createViewModel()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        assertEquals("https://api.openai.com/v1", vm.uiState.value.dialogBaseUrl)
        assertNull(vm.uiState.value.testResult)
    }

    @Test
    fun `updateDialogApiKey updates dialogApiKey and clears testResult`() = runTest {
        val vm = createViewModel()
        vm.updateDialogApiKey("sk-new-key")
        assertEquals("sk-new-key", vm.uiState.value.dialogApiKey)
        assertNull(vm.uiState.value.testResult)
    }

    @Test
    fun `updateDialogModel updates dialogModel and clears testResult`() = runTest {
        val vm = createViewModel()
        vm.updateDialogModel("gpt-4o")
        assertEquals("gpt-4o", vm.uiState.value.dialogModel)
        assertNull(vm.uiState.value.testResult)
    }

    @Test
    fun `updateDialogSystemPrompt updates dialogSystemPrompt`() = runTest {
        val vm = createViewModel()
        vm.updateDialogSystemPrompt("Always respond in JSON.")
        assertEquals("Always respond in JSON.", vm.uiState.value.dialogSystemPrompt)
    }

    @Test
    fun `updateDialogTemperature updates dialogTemperature`() = runTest {
        val vm = createViewModel()
        vm.updateDialogTemperature(1.0f)
        assertEquals(1.0f, vm.uiState.value.dialogTemperature)
    }

    @Test
    fun `toggleDialogUseTemperature sets the flag`() = runTest {
        val vm = createViewModel()
        vm.toggleDialogUseTemperature(true)
        assertTrue(vm.uiState.value.dialogUseTemperature)
        vm.toggleDialogUseTemperature(false)
        assertFalse(vm.uiState.value.dialogUseTemperature)
    }

    @Test
    fun `updateDialogMaxTokens keeps only digit characters`() = runTest {
        val vm = createViewModel()
        vm.updateDialogMaxTokens("1a2b3c")
        assertEquals("123", vm.uiState.value.dialogMaxTokens)
    }

    @Test
    fun `updateDialogMaxTokens accepts purely numeric input`() = runTest {
        val vm = createViewModel()
        vm.updateDialogMaxTokens("4096")
        assertEquals("4096", vm.uiState.value.dialogMaxTokens)
    }

    @Test
    fun `updateDialogMaxTokens strips all non-digit characters`() = runTest {
        val vm = createViewModel()
        vm.updateDialogMaxTokens("abc")
        assertEquals("", vm.uiState.value.dialogMaxTokens)
    }

    // ── isDialogConfigValid ──────────────────────────────────────────────────

    @Test
    fun `isDialogConfigValid is true when baseUrl, apiKey, and model are all set`() = runTest {
        val vm = createViewModel()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        assertTrue(vm.uiState.value.isDialogConfigValid)
    }

    @Test
    fun `isDialogConfigValid is false when baseUrl is blank`() = runTest {
        val vm = createViewModel()
        vm.updateDialogBaseUrl("")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        assertFalse(vm.uiState.value.isDialogConfigValid)
    }

    @Test
    fun `isDialogConfigValid is false when apiKey is blank`() = runTest {
        val vm = createViewModel()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("")
        vm.updateDialogModel("gpt-4")
        assertFalse(vm.uiState.value.isDialogConfigValid)
    }

    @Test
    fun `isDialogConfigValid is false when model is blank`() = runTest {
        val vm = createViewModel()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("")
        assertFalse(vm.uiState.value.isDialogConfigValid)
    }

    // ── isFetchModelsConfigValid ─────────────────────────────────────────────

    @Test
    fun `isFetchModelsConfigValid is true when baseUrl and apiKey are set`() = runTest {
        val vm = createViewModel()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-key")
        assertTrue(vm.uiState.value.isFetchModelsConfigValid)
    }

    @Test
    fun `isFetchModelsConfigValid is false when baseUrl is blank`() = runTest {
        val vm = createViewModel()
        vm.updateDialogBaseUrl("")
        vm.updateDialogApiKey("sk-key")
        assertFalse(vm.uiState.value.isFetchModelsConfigValid)
    }

    @Test
    fun `isFetchModelsConfigValid is false when apiKey is blank`() = runTest {
        val vm = createViewModel()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("")
        assertFalse(vm.uiState.value.isFetchModelsConfigValid)
    }

    // ── saveProvider ─────────────────────────────────────────────────────────

    @Test
    fun `saveProvider closes dialog on success`() = runTest {
        val vm = createViewModel()
        // getProviders() returns 1 provider → allProviders.size == 1 → auto-activate path
        stubSaveProvider(allProviders = listOf(makeProvider(id = "new-id")), activeProvider = null)

        vm.openAddProviderDialog()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-new")
        vm.updateDialogModel("gpt-4")
        vm.saveProvider()

        assertFalse(vm.uiState.value.showProviderDialog)
    }

    @Test
    fun `saveProvider clears saveProviderError on success`() = runTest {
        val vm = createViewModel()
        stubSaveProvider(allProviders = listOf(makeProvider()), activeProvider = null)

        vm.openAddProviderDialog()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        vm.saveProvider()

        assertNull(vm.uiState.value.saveProviderError)
    }

    @Test
    fun `saveProvider derives provider name from URL host when dialogName is blank`() = runTest {
        val vm = createViewModel()
        stubSaveProvider(allProviders = listOf(makeProvider()), activeProvider = null)

        vm.openAddProviderDialog()
        vm.updateDialogName("")
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        vm.saveProvider()

        coVerify {
            repository.saveProvider(match { it.name == "api.openai.com" })
        }
    }

    @Test
    fun `saveProvider uses Unnamed Provider when name is blank and URL has no host`() = runTest {
        val vm = createViewModel()
        stubSaveProvider(allProviders = listOf(makeProvider()), activeProvider = null)

        vm.openAddProviderDialog()
        vm.updateDialogName("")
        vm.updateDialogBaseUrl("not-a-valid-url")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        vm.saveProvider()

        coVerify {
            repository.saveProvider(match { it.name == "Unnamed Provider" })
        }
    }

    @Test
    fun `saveProvider uses trimmed custom name when provided`() = runTest {
        val vm = createViewModel()
        stubSaveProvider(allProviders = listOf(makeProvider()), activeProvider = null)

        vm.openAddProviderDialog()
        vm.updateDialogName("  My Custom Name  ")
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        vm.saveProvider()

        coVerify {
            repository.saveProvider(match { it.name == "My Custom Name" })
        }
    }

    @Test
    fun `saveProvider auto-activates when it is the first provider added`() = runTest {
        val vm = createViewModel()
        // allProviders.size == 1 → first condition in auto-activate logic triggers
        stubSaveProvider(allProviders = listOf(makeProvider(id = "first-id")), activeProvider = null)

        vm.openAddProviderDialog()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        vm.saveProvider()

        coVerify { repository.setActiveProvider(any()) }
    }

    @Test
    fun `saveProvider auto-activates when no active provider currently exists`() = runTest {
        val vm = createViewModel()
        // Two providers already exist but neither is active; hasNoActiveProvider=true triggers
        stubSaveProvider(
            allProviders = listOf(makeProvider(id = "p1"), makeProvider(id = "p2")),
            activeProvider = null  // getActiveProvider() returns null → hasNoActiveProvider=true
        )

        vm.openAddProviderDialog()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        vm.saveProvider()

        coVerify { repository.setActiveProvider(any()) }
    }

    @Test
    fun `saveProvider re-activates the provider when editing the currently active provider`() = runTest {
        val activeProvider = makeProvider(id = "active-id")
        stubDefaultLoadProviders(providers = listOf(activeProvider), activeProvider = activeProvider)
        val vm = createViewModel()
        // state.activeProviderId = "active-id" after init

        // After save: allProviders has the same provider, it's still active
        stubSaveProvider(
            allProviders = listOf(activeProvider),
            activeProvider = activeProvider,
            expectActivate = true
        )

        vm.openEditProviderDialog(activeProvider) // editingProviderId = "active-id"
        vm.saveProvider()
        // provider.id == editingProviderId == "active-id" == state.activeProviderId → auto-activate

        coVerify { repository.setActiveProvider("active-id") }
    }

    @Test
    fun `saveProvider does not auto-activate second provider when first is already active`() = runTest {
        val firstProvider = makeProvider(id = "first-id")
        stubDefaultLoadProviders(providers = listOf(firstProvider), activeProvider = firstProvider)
        val vm = createViewModel()
        // state.activeProviderId = "first-id"

        // After save there are two providers and first is still active
        val secondProvider = makeProvider(id = "second-id")
        coJustRun { repository.saveProvider(any()) }
        // allProviders.size=2 (not 1), state.activeProviderId≠newUUID, hasNoActiveProvider=false
        coEvery { repository.getProviders() } returns listOf(firstProvider, secondProvider)
        coEvery { repository.getActiveProvider() } returns firstProvider
        // setActiveProvider is deliberately NOT stubbed; if called it throws → test fails

        vm.openAddProviderDialog()
        vm.updateDialogBaseUrl("https://openrouter.ai/api/v1")
        vm.updateDialogApiKey("sk-or-key")
        vm.updateDialogModel("llama3")
        vm.saveProvider()

        coVerify(exactly = 0) { repository.setActiveProvider(any()) }
    }

    @Test
    fun `saveProvider sets saveProviderError when repository throws`() = runTest {
        val vm = createViewModel()
        coEvery { repository.saveProvider(any()) } throws RuntimeException("DB write failed")

        vm.openAddProviderDialog()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        vm.saveProvider()

        assertNotNull(vm.uiState.value.saveProviderError)
        assertTrue(vm.uiState.value.saveProviderError!!.isNotBlank())
    }

    @Test
    fun `saveProvider does not close dialog when repository throws`() = runTest {
        val vm = createViewModel()
        coEvery { repository.saveProvider(any()) } throws RuntimeException("Failure")

        vm.openAddProviderDialog()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        vm.saveProvider()

        assertTrue(vm.uiState.value.showProviderDialog)
    }

    @Test
    fun `saveProvider passes null temperature when dialogUseTemperature is false`() = runTest {
        val vm = createViewModel()
        stubSaveProvider(allProviders = listOf(makeProvider()), activeProvider = null)

        vm.openAddProviderDialog()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        vm.toggleDialogUseTemperature(false)
        vm.updateDialogTemperature(0.9f) // value is ignored when useTemperature=false
        vm.saveProvider()

        coVerify { repository.saveProvider(match { it.temperature == null }) }
    }

    @Test
    fun `saveProvider passes temperature value when dialogUseTemperature is true`() = runTest {
        val vm = createViewModel()
        stubSaveProvider(allProviders = listOf(makeProvider()), activeProvider = null)

        vm.openAddProviderDialog()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        vm.toggleDialogUseTemperature(true)
        vm.updateDialogTemperature(0.3f)
        vm.saveProvider()

        coVerify { repository.saveProvider(match { it.temperature == 0.3f }) }
    }

    @Test
    fun `saveProvider parses valid maxTokens string to Int`() = runTest {
        val vm = createViewModel()
        stubSaveProvider(allProviders = listOf(makeProvider()), activeProvider = null)

        vm.openAddProviderDialog()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        vm.updateDialogMaxTokens("2048")
        vm.saveProvider()

        coVerify { repository.saveProvider(match { it.maxTokens == 2048 }) }
    }

    @Test
    fun `saveProvider uses null maxTokens when field is blank`() = runTest {
        val vm = createViewModel()
        stubSaveProvider(allProviders = listOf(makeProvider()), activeProvider = null)

        vm.openAddProviderDialog()
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        vm.updateDialogMaxTokens("")
        vm.saveProvider()

        coVerify { repository.saveProvider(match { it.maxTokens == null }) }
    }

    @Test
    fun `saveProvider trims whitespace from baseUrl before saving`() = runTest {
        val vm = createViewModel()
        stubSaveProvider(allProviders = listOf(makeProvider()), activeProvider = null)

        vm.openAddProviderDialog()
        vm.updateDialogBaseUrl("  https://api.openai.com/v1  ")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        vm.saveProvider()

        coVerify { repository.saveProvider(match { it.baseUrl == "https://api.openai.com/v1" }) }
    }

    @Test
    fun `saveProvider generates new UUID id when adding a provider`() = runTest {
        val vm = createViewModel()
        stubSaveProvider(allProviders = listOf(makeProvider()), activeProvider = null)

        vm.openAddProviderDialog() // editingProviderId = null → UUID.randomUUID() used
        vm.updateDialogBaseUrl("https://api.openai.com/v1")
        vm.updateDialogApiKey("sk-key")
        vm.updateDialogModel("gpt-4")
        vm.saveProvider()

        coVerify { repository.saveProvider(match { it.id.isNotBlank() }) }
    }

    @Test
    fun `saveProvider uses editingProviderId as id when editing existing provider`() = runTest {
        val existing = makeProvider(id = "existing-id")
        val vm = createViewModel()
        stubSaveProvider(allProviders = listOf(existing), activeProvider = existing, expectActivate = true)

        vm.openEditProviderDialog(existing)
        vm.saveProvider()

        coVerify { repository.saveProvider(match { it.id == "existing-id" }) }
    }

    // ── deleteProvider ───────────────────────────────────────────────────────

    @Test
    fun `deleteProvider calls repository deleteProvider with correct id`() = runTest {
        val provider = makeProvider(id = "to-delete")
        stubDefaultLoadProviders(providers = listOf(provider), activeProvider = provider)
        val vm = createViewModel()

        // After delete: no remaining, no active
        coJustRun { repository.deleteProvider("to-delete") }
        coEvery { repository.getProviders() } returns emptyList()
        coEvery { repository.getActiveProvider() } returns null

        vm.deleteProvider("to-delete")

        coVerify { repository.deleteProvider("to-delete") }
    }

    @Test
    fun `deleteProvider reloads provider list after deletion`() = runTest {
        val provider = makeProvider(id = "p-id")
        stubDefaultLoadProviders(providers = listOf(provider), activeProvider = provider)
        val vm = createViewModel()

        coJustRun { repository.deleteProvider("p-id") }
        coEvery { repository.getProviders() } returns emptyList()
        coEvery { repository.getActiveProvider() } returns null

        vm.deleteProvider("p-id")

        assertTrue(vm.uiState.value.providers.isEmpty())
    }

    @Test
    fun `deleteProvider does not call setActiveProvider when no remaining providers`() = runTest {
        val provider = makeProvider(id = "last-id")
        stubDefaultLoadProviders(providers = listOf(provider), activeProvider = provider)
        val vm = createViewModel()

        coJustRun { repository.deleteProvider("last-id") }
        coEvery { repository.getProviders() } returns emptyList()
        coEvery { repository.getActiveProvider() } returns null

        vm.deleteProvider("last-id")

        coVerify(exactly = 0) { repository.setActiveProvider(any()) }
    }

    @Test
    fun `deleteProvider auto-activates first remaining when no active provider after delete`() = runTest {
        val provider = makeProvider(id = "deleted-id")
        val remaining = makeProvider(id = "remaining-id")
        stubDefaultLoadProviders(providers = listOf(provider), activeProvider = provider)
        val vm = createViewModel()

        coJustRun { repository.deleteProvider("deleted-id") }
        // After deletion: getActiveProvider() → null (no active), getProviders() → [remaining]
        coEvery { repository.getActiveProvider() } returns null
        coEvery { repository.getProviders() } returns listOf(remaining)
        coJustRun { repository.setActiveProvider("remaining-id") }

        vm.deleteProvider("deleted-id")

        coVerify { repository.setActiveProvider("remaining-id") }
    }

    @Test
    fun `deleteProvider does not call setActiveProvider when an active provider remains`() = runTest {
        val toDelete = makeProvider(id = "non-active-id")
        val active = makeProvider(id = "active-id")
        stubDefaultLoadProviders(providers = listOf(toDelete, active), activeProvider = active)
        val vm = createViewModel()

        coJustRun { repository.deleteProvider("non-active-id") }
        // Active provider still exists after deletion
        coEvery { repository.getActiveProvider() } returns active
        coEvery { repository.getProviders() } returns listOf(active)

        vm.deleteProvider("non-active-id")

        coVerify(exactly = 0) { repository.setActiveProvider(any()) }
    }

    // ── setActiveProvider ────────────────────────────────────────────────────

    @Test
    fun `setActiveProvider calls repository and updates activeProviderId in state`() = runTest {
        val vm = createViewModel()
        coJustRun { repository.setActiveProvider("new-active-id") }

        vm.setActiveProvider("new-active-id")

        assertEquals("new-active-id", vm.uiState.value.activeProviderId)
        coVerify { repository.setActiveProvider("new-active-id") }
    }

    @Test
    fun `setActiveProvider keeps UI state unchanged when repository throws`() = runTest {
        val active = makeProvider(id = "current-active")
        stubDefaultLoadProviders(providers = listOf(active), activeProvider = active)
        val vm = createViewModel()

        coEvery { repository.setActiveProvider("bad-id") } throws RuntimeException("Not found")

        vm.setActiveProvider("bad-id")

        // activeProviderId should remain as loaded during init
        assertEquals("current-active", vm.uiState.value.activeProviderId)
    }

    // ── testConnection ───────────────────────────────────────────────────────

    @Test
    fun `testConnection sets isTesting false after completion`() = runTest {
        val vm = createViewModel()
        coEvery { repository.testConnection(any()) } returns ApiResult.Success("OK")

        vm.testConnection()

        assertFalse(vm.uiState.value.isTesting)
    }

    @Test
    fun `testConnection sets TestResult Success on successful response`() = runTest {
        val vm = createViewModel()
        coEvery { repository.testConnection(any()) } returns ApiResult.Success("200 OK")

        vm.testConnection()

        val result = vm.uiState.value.testResult
        assertTrue(result is TestResult.Success)
        assertEquals("Connection successful!", (result as TestResult.Success).message)
    }

    @Test
    fun `testConnection sets TestResult Error on failed response`() = runTest {
        val vm = createViewModel()
        coEvery { repository.testConnection(any()) } returns ApiResult.Error("Unauthorized", 401)

        vm.testConnection()

        val result = vm.uiState.value.testResult
        assertTrue(result is TestResult.Error)
        assertEquals("Unauthorized", (result as TestResult.Error).message)
    }

    @Test
    fun `testConnection passes dialog config fields to repository`() = runTest {
        val vm = createViewModel()
        coEvery { repository.testConnection(any()) } returns ApiResult.Success("OK")

        vm.updateDialogBaseUrl("http://localhost:11434/v1")
        vm.updateDialogApiKey("ollama-key")
        vm.updateDialogModel("llama3")
        vm.testConnection()

        coVerify {
            repository.testConnection(
                match { cfg ->
                    cfg.baseUrl == "http://localhost:11434/v1" &&
                    cfg.apiKey == "ollama-key" &&
                    cfg.model == "llama3"
                }
            )
        }
    }

    // ── fetchModels ──────────────────────────────────────────────────────────

    @Test
    fun `fetchModels populates dialogAvailableModels on success`() = runTest {
        val vm = createViewModel()
        val models = listOf("gpt-4", "gpt-3.5-turbo", "gpt-4o")
        coEvery { repository.fetchModels(any()) } returns ApiResult.Success(models)

        vm.fetchModels()

        assertEquals(models, vm.uiState.value.dialogAvailableModels)
        assertFalse(vm.uiState.value.isFetchingModels)
    }

    @Test
    fun `fetchModels sets TestResult Success with model count on success`() = runTest {
        val vm = createViewModel()
        coEvery { repository.fetchModels(any()) } returns ApiResult.Success(
            listOf("model-a", "model-b", "model-c")
        )

        vm.fetchModels()

        val result = vm.uiState.value.testResult
        assertTrue(result is TestResult.Success)
        assertTrue((result as TestResult.Success).message.contains("3"))
    }

    @Test
    fun `fetchModels sets TestResult Error on failure`() = runTest {
        val vm = createViewModel()
        coEvery { repository.fetchModels(any()) } returns ApiResult.Error("Forbidden", 403)

        vm.fetchModels()

        val result = vm.uiState.value.testResult
        assertTrue(result is TestResult.Error)
        assertTrue((result as TestResult.Error).message.contains("Forbidden"))
    }

    @Test
    fun `fetchModels sets isFetchingModels false after completion`() = runTest {
        val vm = createViewModel()
        coEvery { repository.fetchModels(any()) } returns ApiResult.Success(emptyList())

        vm.fetchModels()

        assertFalse(vm.uiState.value.isFetchingModels)
    }

    // ── clearTestResult ──────────────────────────────────────────────────────

    @Test
    fun `clearTestResult removes testResult from state`() = runTest {
        val vm = createViewModel()
        coEvery { repository.testConnection(any()) } returns ApiResult.Success("OK")
        vm.testConnection()
        assertNotNull(vm.uiState.value.testResult)

        vm.clearTestResult()

        assertNull(vm.uiState.value.testResult)
    }
}
