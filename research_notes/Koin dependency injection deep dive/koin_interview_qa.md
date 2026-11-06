# Koin Dependency Injection — Interview Q&A Deep Dive

## What is a Koin module, and how/when are modules loaded?

### Takeaway
A Koin module is a declarative grouping of dependency definitions (`single`, `factory`, `scoped`) that is lazily inert until `startKoin` (or `loadKoinModules`) registers it into a running container; nothing is instantiated until a definition is actually requested.

### Cited Findings
- A module "groups related definitions, usually one module per feature or layer" and "is only a description. Nothing is created until Koin starts and an instance is requested." — [Koin Core Concepts](https://insert-koin.io/docs/intro/core-concepts)
- Modules can `include` other modules, so a large app becomes a tree of small modules. — [Koin Core Concepts](https://insert-koin.io/docs/intro/core-concepts)
- Classic DSL example: `val appModule = module { singleOf(::ApiService) }`; newer Compiler Plugin DSL: `module { single<ApiService>() }`; Annotations DSL: `@Module @ComponentScan("com.example.app") class AppModule`. — [Koin Core Concepts](https://insert-koin.io/docs/intro/core-concepts)
- `startKoin { }` creates the container (a `KoinApplication`) and loads modules via `modules(appModule)`. Call it once, at the app's entry point; on Android this is `Application.onCreate()`, passing `androidContext(this)`. — [Koin Core Concepts](https://insert-koin.io/docs/intro/core-concepts)
- With the Compiler Plugin/Annotations approach, you can declare `@KoinApplication(modules = [AppModule::class])` on a class and call `startKoin<MyApp>()`. — [Koin Core Concepts](https://insert-koin.io/docs/intro/core-concepts)

### Inferences
- Because module registration is just metadata (no eager instantiation), you can safely define far more bindings than a given app flow needs — only requested types pay any creation cost. This is a frequent interview talking point contrasting Koin with compile-time graphs that validate/know everything upfront.
- `loadKoinModules` (for loading modules after `startKoin`, e.g., dynamic/feature modules) is a well-known Koin API in practice, but the fetched Core Concepts page did not cover it in detail — treat the exact runtime semantics (whether it merges into the existing container live) as needing direct doc/source confirmation if asked for low-level detail in an interview.

### Gaps
- Did not find the official page text specifically documenting `loadKoinModules` semantics (e.g., whether late-loaded modules can override existing definitions, or whether unloading is also supported via `unloadKoinModules`). Flag as a gap; the general behavior is well known in the community (used for on-demand/feature-module DI) but this research did not fetch a primary source for it.

---

## Difference between `single`, `factory`, and `scoped` — with concrete scenarios

### Takeaway
`single` = one instance per container (good for Repositories/singleton services), `factory` = a brand-new instance every time it's resolved (good for stateless use cases/interactors), `scoped` = one instance per declared scope's lifetime (good for per-screen/per-session objects like a cart or user-session object).

### Cited Findings
- `single`: "Keeps one instance for the whole container." DSL: `singleOf(::UserRepository)` / `single<UserRepository>()` / annotation `@Singleton`. — [Koin Core Concepts](https://insert-koin.io/docs/intro/core-concepts)
- `factory`: "Creates a new instance on every request" — described as short-lived and not persisted/shared in the container. — [Koin Core Concepts](https://insert-koin.io/docs/intro/core-concepts); [itnext.io summary via search](https://itnext.io/mastering-koin-scopes-beyond-singletons-and-factories-dae8b58ef54f)
- `scoped`: "Ties an instance's lifetime to a scope, such as a user session, rather than to the container." Declared inside `scope<UserSession> { scopedOf(::CartRepository) }` (classic DSL) or `scoped<CartRepository>()` (Compiler Plugin DSL); annotation form is `@Scope(UserSession::class)` + `@Scoped`. The scope is opened with `createScope` and released with `close()`, freeing the scoped instances. — [Koin Core Concepts](https://insert-koin.io/docs/intro/core-concepts)
- Community framing: a typical `scoped` case is "a user session that gets torn down on logout." — [search synthesis of Koin docs/ItNext](https://insert-koin.io/docs/reference/koin-core/scopes)

### Inferences
- Mapping to the user's suggested scenarios is directly supported by the docs' own semantics: **Repository as `single`** fits because repositories typically wrap data sources that should be shared app-wide (DB/network clients) — matches "one instance for the whole container." **Use case/interactor as `factory`** fits because use cases are usually stateless, cheap-to-construct classes that take parameters per call — matches "new instance on every request," avoiding stale captured state. **Per-screen object as `scoped`** fits Koin's own example of a scope tied to a screen/session lifetime (e.g., `activityScope`/`fragmentScope`/`viewModelScope` on Android) rather than the whole app container.
- Interviewers sometimes push on "why not just use `factory` everywhere instead of `scoped`?" — the answer from the docs is that `scoped` instances are shared *within* a scope (multiple classes in the same scope get the *same* instance), whereas `factory` always creates a fresh, non-shared instance even within the same resolution context.

### Gaps
- The official Core Concepts page gave no code sample for `factory` (only a one-line description), so there's no primary-source canonical example to quote verbatim beyond the description text.

---

## Koin scopes and Android lifecycle (Activity/Fragment/ViewModel), and avoiding memory leaks

### Takeaway
Koin ships purpose-built scope archetypes (`activityScope`, `activityRetainedScope`, `fragmentScope`, `viewModelScope`) that hook into Android lifecycle callbacks and auto-close on `ON_DESTROY`/`onCleared()`; the main leak vector is scopes created manually via `getKoin().createScope()`, which have no lifecycle and must be closed by hand.

### Cited Findings
- Koin provides one scope archetype per component type: `activityScope`, `activityRetainedScope`, `fragmentScope` (DSL) or `@ActivityScope`, `@ActivityRetainedScope`, `@FragmentScope` (annotations). — [Koin Android Scope docs](https://insert-koin.io/docs/4.1/reference/koin-android/scope)
- Activity binding: extend `ScopeActivity` or implement `AndroidScopeComponent` with `override val scope: Scope by activityScope()`. Lookups from `inject()`/`get()`/`viewModel()` check the Activity scope first, then the root scope. The scope closes on `ON_DESTROY` (still open during your own `onDestroy()` code). — [Koin Android Scope docs](https://insert-koin.io/docs/4.1/reference/koin-android/scope)
- Retained Activity scope (`RetainedScopeActivity`/`activityRetainedScope()`) survives configuration changes; Koin stores it in a ViewModel and closes it when that ViewModel is cleared (i.e., when the Activity finishes). Don't hold references to Activity/Views/Fragments in retained definitions — `Context` resolves to the Application here. — [Koin Android Scope docs](https://insert-koin.io/docs/4.1/reference/koin-android/scope)
- Fragment scope links to the host Activity's scope (so it can see Activity-scoped instances) unless you pass `useParentActivityScope = false`. It closes on the Fragment's `ON_DESTROY`, **not** `onDestroyView()` — so release View references yourself in `onDestroyView()`. — [Koin Android Scope docs](https://insert-koin.io/docs/4.1/reference/koin-android/scope)
- ViewModel scope: declare with `viewModelScope { }` / `@ViewModelScope`; either enable `viewModelScopeFactory()` so Koin creates a scope per ViewModel, or extend `ScopeViewModel`. Don't declare a ViewModel with `scoped` inside an `activityScope` — the ViewModelStore won't manage it. — [Koin Android Scope docs](https://insert-koin.io/docs/4.1/reference/koin-android/scope)
- Official best-practice framing: a manually created scope that is never closed is treated as a memory leak; recommendation is to close it in `onDestroy`. — [Koin Android Best Practices](https://insert-koin.io/docs/reference/koin-android/best-practices)
- Scopes created with `getKoin().createScope()` have no lifecycle and must be closed manually; lifecycle-bound scopes (`activityScope()`, etc.) close automatically. — [Koin Android Best Practices](https://insert-koin.io/docs/reference/koin-android/best-practices)
- Explicit leak-avoidance rules from the docs: don't pass an Activity into a `single` (keeps the first Activity alive forever — use Application `Context` or an Activity-scoped class instead); instances in `activityRetainedScope` must never reference an Activity/View/Fragment; never inject Activity- or Fragment-scoped instances into a ViewModel; don't keep Views/bindings in Fragment-scoped instances. — [Koin Android Best Practices](https://insert-koin.io/docs/reference/koin-android/best-practices)
- A real-world LeakCanary trace on GitHub showed a `Scope` holding a Fragment through the `scopeRegistry` — but the reporter was on an old version (`koin-androidx-ext 2.2.0`), so it may not reflect current releases. — [GitHub issue #953](https://github.com/InsertKoinIO/koin/issues/953)
- For cleanup hooks: override `onCloseScope()` on an `AndroidScopeComponent` (called before Activity/Fragment scope closes, but not for retained or Service scopes); Service scopes open in `onCreate()`/close in `onDestroy()` via `ScopeService` or `destroyServiceScope()`; per-definition cleanup can use `onClose { }`. — [Koin Android Scope docs](https://insert-koin.io/docs/4.1/reference/koin-android/scope)
- Classes that inject scoped definitions need a scope, or resolution fails with `NoDefinitionFoundException`; Activity/Fragment scopes must be created on the main thread. — [Koin Android Scope docs](https://insert-koin.io/docs/4.1/reference/koin-android/scope)

### Inferences
- The "why do scopes leak" interview answer boils down to: a scope is just a `HashMap`-like registry of instances keyed to an id; if nothing ever calls `.close()` on it, the registry (and everything it holds, including anything that transitively captured an Activity/Fragment/View) stays reachable from Koin's global scope registry, defeating garbage collection — this matches the "unclosed scope keeps its scoped instances alive, so they behave like singletons" explanation found in a secondary source. — [devapro.github.io](https://devapro.github.io/en/2023/12/21/koin-scopes) (secondary, consistent with official docs' framing)
- A secondary/aggregator source argued "Koin itself doesn't directly cause leaks... leaks come from unclosed scopes or stray references" — this is consistent with the official docs' own leak-avoidance checklist, so it's a reasonable practitioner summary, but it's not an authoritative citation on its own. — [coderapp.vercel.app](https://coderapp.vercel.app/answer/79250583) (secondary; weight below official docs)

### Gaps
- None major; this area is well-documented by Koin's own docs across versions 4.0/4.1.

---

## Does Koin detect circular dependencies? What error occurs, and how do you resolve a circular design problem?

### Takeaway
Detection depends on the Koin version/tooling: the dedicated cycle detector that existed in Koin 2.1.x was removed for performance reasons, so in plain runtime usage a cycle commonly surfaces as a `StackOverflowError` (or, in some reported cases, a resolution exception); the **Koin Compiler Plugin** (build-time) and **`verify()`** (test-time) are the supported ways to catch cycles deterministically today. The fix is always a design fix: break the cycle via `Lazy<T>` injection on one side (without resolving it eagerly in `init`) or, better, extract the shared responsibility into a third class both sides depend on.

### Cited Findings
- Koin's troubleshooting docs: the **Koin Compiler Plugin build error** is `"[Koin][KOIN-D004] Circular dependency detected:"`, which suggests breaking the cycle with `Lazy<T>` or refactoring. — [Koin troubleshooting docs, via research synthesis](https://insert-koin.io/docs/reference/koin-core/troubleshooting)
- A `verify()` test-time failure message: `"Circular injection between ... Fix your Koin configuration!"`, reported as the `CircularInjectionException`. — [Koin troubleshooting docs](https://insert-koin.io/docs/reference/koin-core/troubleshooting); [Koin verify() docs](https://insert-koin.io/docs/reference/koin-test/verify)
- Without either check, a cycle causes a `StackOverflowError` at runtime. — [Koin troubleshooting docs](https://insert-koin.io/docs/reference/koin-core/troubleshooting)
- A Koin maintainer, responding on Kotlin Slack to a reported `StackOverflowError` at startup, said a cycle-dependency detector *existed in Koin 2.1.x but was removed for performance reasons* — meaning older/some current runtime paths will not give a clean error message for a cycle, just a stack overflow. — [Kotlin Slack thread, summarized in search](https://slack-chats.kotlinlang.org/t/16364259/hey-i-meet-the-stackoverflow-exception-and-this-is-my-code-s)
- A separately reported runtime exception from an older Koin version: `DependencyResolutionException: Cyclic call while resolving Bean[class=ExchangeManager]`. — [Kotlin Slack thread](https://slack-chats.kotlinlang.org/t/499576/hi-everyone-i-want-to-ask-regarding-memory-usage-between-sin) (exact source thread for this message was a related Slack discussion found in search; treat version-specificity with caution)
- Fix guidance from docs/community: inject `Lazy<T>` on one side of the cycle and don't read `.value` inside the constructor or an `init` block (that would just re-trigger eager resolution and the same crash); alternatively move the shared logic into a third class that both original classes depend on, removing the cycle entirely. — [Koin troubleshooting docs](https://insert-koin.io/docs/reference/koin-core/troubleshooting); [Kotlin Slack discussion on TaskService/TaskGroupService cycle](https://slack-chats.kotlinlang.org/t/13842326/hi-all-good-morning-i-have-a-module-in-which-i-created-a-sim)

### Inferences
- A strong interview answer should note the *nuance* most blog posts miss: "Koin detects circular dependencies" is an oversimplification. Detection is tooling-dependent (Compiler Plugin at build time, `verify()`/`checkModules()` at test time) rather than a guaranteed built-in runtime safety net in all versions — a naive runtime resolution of a true A→B→A cycle can still blow the stack rather than fail cleanly, unless the newer build/test-time tooling is in use.
- This is a good place to flag the common "Koin == true DI container with the same guarantees as Dagger" misconception: Dagger/Hilt reject a cyclic graph unconditionally at compile time; Koin's compile-time safety is opt-in (Compiler Plugin) and even then has documented scope limits (see verify()/Compiler Plugin limitations below).

### Gaps
- Could not find the *exact, current* (2024/2025 Koin 4.x, non-plugin) default runtime exception type/message for a simple two-class constructor cycle from an authoritative, version-pinned source — evidence conflicts across versions (StackOverflowError vs. `DependencyResolutionException` vs. plugin's `KOIN-D004`). An interview answer should present this as "it depends on whether you're using the Compiler Plugin / verify(), and which Koin version" rather than quoting one single canonical message.

---

## How do you unit test code that uses Koin (KoinTest, checkModules/verify, module overriding, koinApplication, mocking)?

### Takeaway
Koin's test story has shifted from the deprecated `checkModules()` (which actually instantiates definitions and needs real/mocked values for every dependency) to `verify()` (a lighter, constructor-signature-only static check added in Koin 3.3+, JVM-only) and, increasingly, the Koin Compiler Plugin for full compile-time graph validation; for behavioral unit tests, `KoinTest` + module overriding, or a fully isolated `koinApplication { }` container, are the standard patterns, with mocks supplied either via test modules or `MockProviderRule`.

### Cited Findings
- `verify()` is an extension function on `Module`, available on the JVM via `koin-test`; it reads each definition's constructor and checks that every parameter type has a matching definition in the module or its included modules. "It runs no definition and needs no mock." — [Koin verify() docs](https://insert-koin.io/docs/reference/koin-test/verify)
- A constructor parameter is considered satisfied if it is: declared (a matching definition exists), optional (has a default value — `verify()` warns if nothing matches), wrapped (`Lazy<T>`/`List<T>` where `T` is declared), whitelisted (`extraTypes` or a basic type like `String`/`Int`/`Long`/`Double`), or injected/provided (`@InjectedParam`, `@Provided`, or declared via `injections`). — [Koin verify() docs](https://insert-koin.io/docs/reference/koin-test/verify)
- Usage forms: `appModule.verify()` (checks module + includes); `listOf(dataModule, uiModule).verifyAll()` for modules not linked via `includes()`; `verify(extraTypes = ...)` for platform-supplied types; `androidVerify()` from `koin-android-test` adds common Android types (`Context`, `Activity`, `SavedStateHandle`); `verify(injections = ...)` for values passed through `parametersOf()`. — [Koin verify() docs](https://insert-koin.io/docs/reference/koin-test/verify)
- Errors from `verify()`: `MissingKoinDefinitionException` (missing dependency, with a suggested fix) and `CircularInjectionException` (cycle between constructors). — [Koin verify() docs](https://insert-koin.io/docs/reference/koin-test/verify)
- `verify()` limitations: checks constructors only — it does **not** check qualifiers, properties, or `get()` calls made inside a definition lambda body; parameters passed via `parametersOf()` are reported missing unless declared; runtime-provided types are reported missing unless listed in `extraTypes` or covered by `androidVerify()`. — [Koin verify() docs](https://insert-koin.io/docs/reference/koin-test/verify)
- `checkModules()` is **deprecated since Koin 4.0** in favor of `verify()` or the Compiler Plugin. Unlike `verify()`, `checkModules()` actually launches the modules and tries to create each definition, so it needs real values supplied for anything resolved at runtime — via its own DSL: `withInstance<T>()` (mock/stub for a type), `withInstance("value")` (default for all matching definitions), `withParameter<T>`, `withProperty(key, value)`, `withScopeLink(...)`. Mocked types additionally need a `MockProviderRule`. — [Koin checkModules() docs](https://insert-koin.io/docs/reference/koin-test/checkmodules); synthesis confirmed in search results
- The Koin Compiler Plugin now provides compile-time dependency validation (missing dependencies, qualifier mismatches, broken call sites), which the docs say "replaces the need for verify() and checkModules() in most cases" — though `verify()` is still needed where the plugin doesn't reach: module lists built with `if`/`when` or varargs (flagged `KOIN-W003`), library modules without their own entry point, and lazy modules. — [Koin verify() docs](https://insert-koin.io/docs/reference/koin-test/verify)
- For isolated test containers: a library or test can build its own container with `koinApplication { }` rather than `startKoin`, avoiding contention with the global context (if a library calls `startKoin`, it fights the host app for that single global slot, and its definitions can override the app's). — [Koin context isolation docs](https://insert-koin.io/docs/reference/koin-core/context-isolation); [proandroiddev.com: Koin's Isolated Context](https://proandroiddev.com/koins-isolated-context-803e8047b358)
- Because `KoinTest` defaults to reading from the *global* context, a test that wants to point at an isolated `koinApplication` instance has to override `getKoin()` to return that isolated instance and load test modules into it — this pattern is described in Koin's own docs and corroborated by a Kotlin Slack thread noting `KoinTest` only overrides `GlobalContext`'s koin by default. — [search synthesis of Koin docs](https://insert-koin.io/docs/reference/koin-core/context-isolation); [Kotlin Slack thread](https://slack-chats.kotlinlang.org/t/12327500/hey-devs-i-would-like-to-know-your-thoughts-on-testing-koin-)
- For Ktor specifically, Koin's own testing docs recommend creating test modules that **override** production dependencies with mocks/fakes, and suggest using an isolated context to prevent test-to-test interference. — [Koin Ktor testing docs](https://insert-koin.io/docs/reference/koin-ktor/ktor-testing)
- Practical test-module override pattern reported in search: supply a fake binding directly, e.g. `single<SdkService> { FakeSdkService() }`, or substitute a mockk-created mock for the same type, inside a dedicated test module loaded on top of (or instead of) the production one. — [search synthesis]

### Inferences
- A clean interview answer should separate three distinct Koin testing tools by purpose: (1) `verify()`/Compiler Plugin = **static graph wiring checks**, no instances created, fast, used to catch "did I forget a binding"; (2) `checkModules()` = **older, heavier, actually instantiates things**, now deprecated; (3) `KoinTest` + module overriding / `koinApplication` = **behavioral unit/integration tests** where you actually want working or faked instances resolved through DI, the same as you'd do with any DI framework's test doubles.
- The shift from `checkModules()` → `verify()` → Compiler Plugin mirrors Koin's broader multi-year move from a pure-runtime-reflection model toward opt-in compile-time safety, which is itself a good talking point for the "service locator" criticism question below.

### Gaps
- Did not fetch the primary `checkmodules.md` doc page directly (relied on search-engine synthesis of it); if an interviewer wants verbatim DSL syntax for `checkModules()`, double-check against `https://insert-koin.io/docs/reference/koin-test/checkmodules` directly since it's now a deprecated/legacy page that may have been trimmed in newer doc versions.

---

## Service locator criticism of Koin: pros/cons and how Koin mitigates it

### Takeaway
Koin is accurately described as a **service-locator-based** DI framework rather than framework-driven constructor injection like Dagger/Hilt: dependencies are resolved by calling into a container (`get()`, `by inject()`) rather than purely received via declared constructor parameters wired by generated code, which trades compile-time graph guarantees for flexibility and less boilerplate; Koin mitigates the "inject anything anywhere" criticism by encouraging a constructor-injection *style* inside definitions (`single { ClassName(get(), get()) }`), so the classes themselves stay testable with plain constructor calls even though Koin's container is a locator under the hood.

### Cited Findings
- Core criticism, as voiced by practitioners: Koin (and Kodein) "do the job, but they're still service locators at their core and don't automatically inject dependencies like Dagger does," and the service-locator pattern "makes it far too easy to write bad code because you can inject anything anywhere." — [search synthesis of blog/forum commentary, original essay referenced is Zac Sweers' DI-vs-service-locator piece, not directly opened]
- A specific counter-framing on where the "service locator" line actually falls: it only becomes a service locator pattern "if you put them into a Map and then get them with something like a generic get" — otherwise using `single { ClassName(get(), get()) }`-style constructor wiring at the definition site keeps the actual application classes decoupled from Koin (no `by inject()` scattered through business logic), which is close to ordinary constructor injection just configured at a different layer. — [Reddit/forum discussion, search synthesis](https://redlib.hbubli.cc/r/androiddev/comments/d1b4i1/understanding_the_difference_between_di_and_sl/eznxgce)
- Another framing from Kotlin Slack: `by inject()` usage (field/property injection, resolved lazily from the container at the call site) is the part that's genuinely "service locator," whereas constructor injection (even when the constructor args are supplied by Koin's DSL) is closer to traditional DI; Koin's own stance is that the honest answer is "both" — it supports both styles and the risk profile depends on which one a team actually uses. — [Kotlin Slack thread, search synthesis](https://slack-chats.kotlinlang.org/t/470623/i-m-sorry-if-this-has-already-been-debated-in-here-but-i-rea)
- Runtime-checking objection: because Koin resolves everything at runtime by default, there are "no static guarantees" the way there are with Dagger's compile-time-generated graph — this is described as the main substantive argument against Koin relative to Dagger. — [search synthesis of forum/Stack Exchange-style discussion]
- Even Hilt is noted to have a *locator-style* seam at Android framework entry points — e.g., `hiltViewModel()` in a composable is structurally similar to `koinViewModel()` — meaning the service-locator critique is really about *how much* of an app's code touches the container directly, not a binary distinction. — [Kotlin Slack thread discussion](https://slack-chats.kotlinlang.org/t/33094191/dagger-vs-hilt-vs-koin-vs-metro-vs-lt-what-comes-next-gt-ser)
- Performance claims are disputed across sources: one DEV.to post claims Koin is "slightly slower than Dagger 2 and Hilt, especially in large-scale applications," while a 2024 benchmark article instead credits Koin with avoiding reflection overhead and faster build times. — [dev.to](https://dev.to/mikkelofficial7/koin-vs-dagger-2-vs-hilt-which-one-should-we-use-1e0n); [proandroiddev.com benchmark](https://proandroiddev.com/benchmarking-koin-vs-dagger-hilt-in-modern-android-development-2024-ff7bb40470df)

### Inferences
- A strong interview answer distinguishes **container mechanism** (how Koin finds a dependency — a runtime registry lookup, i.e., structurally a service locator) from **usage discipline** (whether application code calls into that registry directly via `by inject()`/`get()` scattered everywhere, vs. confining all container calls to the module definitions themselves, with ordinary classes taking plain constructor parameters). Koin's documented idiom of `single { Foo(get(), get()) }` is explicitly the latter — it keeps `Foo` itself free of any Koin import, so `Foo` can be unit-tested with `Foo(fakeA, fakeB)` with zero DI framework involvement, which directly defuses the "hidden dependencies" critique for that class even though the wiring glue is still locator-style.
- Testability concern in practice: the criticism that "service locators hide a class's dependencies" is weaker when teams enforce constructor-style definitions (as Koin docs/examples show), and strongest when teams lean on scattered `by inject()`/`get()` calls inside business logic classes — this is a useful "it depends on team discipline" framing for an interview answer rather than a flat verdict either way.

### Gaps
- Did not directly open or quote Zac Sweers' essay on DI vs. service locators, which multiple secondary sources cite as the most authoritative/most-referenced take in this debate — flagged here as a primary source worth reading directly if deeper citation is needed, but not verified firsthand in this research pass.
- No rigorous, independently reproduced performance benchmark was found; both "Koin is slower" and "Koin is faster/comparable" claims come from blog-level sources that disagree, so this should be presented as a disputed/anecdotal area, not a settled fact.

---

## Common runtime errors and how to debug them

### Takeaway
The most common Koin runtime failures are `NoDefinitionFoundException` (binding not found/wrong scope/qualifier), `InstanceCreationException` (binding found but construction threw — check the nested `Caused by:`), and a family of scope-related exceptions (`ClosedScopeException`, `ScopeNotCreatedException`, `ScopeAlreadyCreatedException`, `MissingScopeValueException`); Koin's logger (`androidLogger(Level.DEBUG)`) filtered on `[Koin]` in Logcat is the primary debugging tool, alongside the Compiler Plugin to catch missing definitions before runtime.

### Cited Findings
- **`NoDefinitionFoundException`** (the troubleshooting page's closest equivalent to the commonly-searched-for `NoBeanDefFoundException`, which is not actually a Koin exception name): message format is `"No definition found for type 'com.example.UserRepository' on scope '['_root_']'."` Causes: the module isn't loaded, the qualifier doesn't match, the interface isn't bound to its implementation, or the definition is scoped and the request comes from a different scope. Fixes: add the module via `modules(...)`/`includes(...)`, match qualifiers on both sides, bind the interface explicitly, request from the correct scope. — [Koin troubleshooting docs](https://insert-koin.io/docs/reference/koin-core/troubleshooting)
- **`InstanceCreationException`**: message format `"Could not create instance for '[Singleton: 'com.example.UserRepository']'"`. The definition was found, but constructing it threw; the real underlying error is in the nested `Caused by:` entry (often a missing constructor parameter or an exception thrown from within the constructor). Fix: resolve the nested exception, not the Koin wrapper. — [Koin troubleshooting docs](https://insert-koin.io/docs/reference/koin-core/troubleshooting)
- **Scope-closed / scope-lifecycle errors**:
  - `ClosedScopeException` — `"Scope 'user_session' is closed"` — code requested an instance from an already-closed scope, often from a callback or coroutine that outlived its screen.
  - `ScopeNotCreatedException` — `"No scope found for id 'user_session'"` — `getScope(id)` called before the scope was created; use `getOrCreateScope` or create it first.
  - `ScopeAlreadyCreatedException` — `"Scope with id 'user_session' is already created"` — `createScope` called twice with the same id.
  - `MissingScopeValueException` — `"No value for scope 'user_session' in [Scoped: ...]"` — a value declared with `declare()` wasn't actually supplied for that scope.
  - Prevention: never store scoped instances in a `single` or a static field (they'd outlive their scope); only inject scoped instances into objects in the same scope or a shorter-lived one. — [Koin troubleshooting docs](https://insert-koin.io/docs/reference/koin-core/troubleshooting)
- Debugging tooling: enable `androidLogger(Level.DEBUG)` while investigating, filter Logcat on `[Koin]`, and switch to `Level.ERROR`/`Level.NONE` in release builds; use the Compiler Plugin to catch missing definitions at build time instead of discovering them at runtime. — [Koin Android Best Practices](https://insert-koin.io/docs/reference/koin-android/best-practices)

### Inferences
- The widely-searched term "`NoBeanDefFoundException`" appears to be either a very old/renamed Koin exception, a conflation with Spring's `NoSuchBeanDefinitionException`, or informal community shorthand — current Koin docs use `NoDefinitionFoundException` for this failure mode. An interview answer should mention both names (in case the interviewer uses the older/informal term) but cite `NoDefinitionFoundException` as the accurate, current one.
- `InstanceCreationException` is a wrapper exception — the actual debugging value is almost always in the nested cause, which is a good "gotcha" to call out: junior developers sometimes paste the outer exception into a search engine instead of reading `Caused by:`.

### Gaps
- None significant for this question; the troubleshooting docs cover this comprehensively.

---

## Best practices: module organization, qualifiers, avoiding field injection / god modules

### Takeaway
Official and community guidance converges on: organize modules by feature/layer rather than one giant module, use qualifiers sparingly and prefer type-safe qualifiers (enum/type-based) over raw strings, and prefer constructor-style definitions (`single { Foo(get(), get()) }` / `singleOf(::Foo)`) over scattering `by inject()`/field injection through business logic.

### Cited Findings
- Koin's official qualifiers guidance: use qualifiers sparingly — adding named qualifiers to types that are already distinct "adds noise without benefit"; document the qualifiers you do use. — [Koin Qualifiers docs](https://insert-koin.io/docs/reference/koin-core/qualifiers)
- Qualifier mechanisms, from strongest to weakest type-safety: **type qualifiers** (e.g., `object LocalDb` used via `named<LocalDb>()`) — typos become compile errors and IDE renames update every use; **enum qualifiers** (`named(DbType.LOCAL)`, equivalent to `named("local")` using the lowercase entry name); **named string qualifiers** (`named("local")`) — most error-prone, since "`encrypted`" and "`encrypted_client`" are different qualifiers and typos are only caught at runtime via `NoDefinitionFoundException`. — [Koin Qualifiers docs](https://insert-koin.io/docs/reference/koin-core/qualifiers)
- Explicit best practice: only add a qualifier when two definitions genuinely share the same type; if they play different roles, prefer distinct types/interfaces (e.g., `LocalDatabase` vs. `RemoteDatabase`) instead of qualifying one generic type — "the compiler catches type mistakes but not wrong qualifier strings." — [Koin Qualifiers docs](https://insert-koin.io/docs/reference/koin-core/qualifiers)
- Constructor-based shortcuts like `singleOf` resolve constructor parameters by type only, so if you need a qualified parameter you must write out the constructor call manually with `get(named(...))` instead of using the shortcut. — [Koin Qualifiers docs](https://insert-koin.io/docs/reference/koin-core/qualifiers)
- Module organization: "organizing modules by feature" is listed as a key practice for large apps, alongside "using scopes for component lifecycles" and "establishing conventions to minimize runtime errors." — [devbytes.co.in, cited via search](https://devbytes.co.in/news/5-best-practices-for-using-koin-in-large-android-applications)
- A practitioner Medium article reports the god-module symptom directly: "the Koin module definition file starts to be hundreds of lines big and pretty unreadable," and recommends splitting each module into its own file, then wrapping modules behind a provider interface to make them easier to find/load; the same article notes named qualifiers only need to be unique *within their module*, though global uniqueness is safer. — [medium.com/@ametric: Organizing Koin modules](https://medium.com/@ametric/organizing-koin-modules-ad2bdd956838)
- A KMP-focused guide recommends splitting `commonMain` DI modules into separate files by concern and aggregating them in one init function, using `named()` only when there are genuinely multiple instances of the same type. — [skills.lc KMP Koin guide, cited via search](https://www.skills.lc/hieuwu/agent-skills/hieuwu-agent-skills-koin-di-setup-skill-md)
- Koin's Android Best Practices page does not explicitly compare field vs. constructor injection on its own page, but all of its example code for avoiding leaks favors scope-aware constructor-style definitions and scoped delegates over raw `by inject()` field access inside long-lived classes. — [Koin Android Best Practices](https://insert-koin.io/docs/reference/koin-android/best-practices)

### Inferences
- "Avoiding field injection in favor of constructor-style definitions" in a Koin context specifically means: keep `by inject()`/`get()` calls confined to the module definition lambdas (where Koin assembles the object graph) and to Android framework entry points (Activity/Fragment/ViewModel delegates, which have no other way to receive DI), rather than using `by inject()` as a property inside arbitrary business-logic classes — the latter is what most directly triggers the "service locator smell" criticism discussed above.
- The "god module" anti-pattern isn't named verbatim in Koin's own docs, but the Medium source's description ("hundreds of lines, unreadable") plus the devbytes "organize by feature" advice map directly onto it — split-by-feature/layer is the consistently recommended remedy across both official best practices framing and community blog posts.

### Gaps
- Koin's official docs do not appear to have a single consolidated "best practices for module organization" reference page distinct from the Android-specific best-practices page; this section leans more heavily on community blog sources (Medium, devbytes) than for other questions in this report. Treat the god-module terminology as community shorthand, not an official Koin term.

---

## Pros/cons: when is Koin a good fit vs. when is a compile-time framework (Dagger/Hilt) preferable?

### Takeaway
Per Koin's own comparison page, the trade-off is explicit: Hilt/Dagger give full compile-time graph verification, zero runtime lookup cost, and first-party Android/Google backing (better for large, Java-inclusive, enterprise-standardized codebases); Koin gives less boilerplate, dynamic module loading/unloading, and true Kotlin Multiplatform support across Android/iOS/desktop/web/JVM (better for multiplatform projects, smaller/faster-moving teams, and teams that want to avoid annotation-processing setup) — with the Koin Compiler Plugin narrowing but not eliminating the compile-time-safety gap.

### Cited Findings
- Hilt generates its DI graph as Java source at build time — "every binding, component and scope is known when the app compiles" — whereas Koin keeps the graph in a runtime container (though its Compiler Plugin can additionally check the graph at build time and generate constructor wiring). — [Koin's own "Koin vs Hilt/Dagger" docs](https://insert-koin.io/docs/intro/koin-vs-hilt)
- Where Koin's own docs concede Hilt is stronger: full graph verification on every build for every module/scope (Koin's plugin covers entry points but not runtime-loaded or standalone library modules); no runtime lookup cost; Java support (Koin's API targets Kotlin); official Android/Google guidance and first-party integration; fuller error paths from entry point to missing binding. — [Koin's own "Koin vs Hilt/Dagger" docs](https://insert-koin.io/docs/intro/koin-vs-hilt)
- Where Koin's own docs claim Koin is stronger: Kotlin Multiplatform support across Android, iOS, desktop, web, and JVM servers (Hilt is Android-only); modules can be loaded, unloaded, or loaded in the background after startup; less setup (no annotation processor, no generated sources, no `@AndroidEntryPoint`); less boilerplate (no `@Inject` on constructors, no separate `@Binds` module for interfaces); a choice between DSL and annotations that can be mixed. — [Koin's own "Koin vs Hilt/Dagger" docs](https://insert-koin.io/docs/intro/koin-vs-hilt)
- On performance/build time, Koin's own comparison page makes **no quantified claim** — it only notes qualitatively that Koin adds a per-resolution lookup cost and startup registration work that Hilt avoids. — [Koin's own "Koin vs Hilt/Dagger" docs](https://insert-koin.io/docs/intro/koin-vs-hilt)
- External sources disagree on real-world build time and runtime performance: one source claims Hilt's annotation processing adds build time that scales with app/module/dependency count, and that Koin has "minimal impact on build time and no annotation-processing overhead"; a droidcon 2024 benchmark instead frames it as "one is app runtime performance optimized and the other is build time performance optimized," i.e., a genuine trade-off rather than Koin being strictly faster. — [search synthesis, droidcon benchmark article](https://www.droidcon.com/2024/12/03/benchmarking-koin-vs-dagger-hilt-in-modern-android-development-2024/)
- Multiplatform is called out as Koin's strongest practical differentiator by multiple independent sources, not just Koin's own docs. — [search synthesis]
- No solid, data-backed source was found specifically correlating team size with recommended framework choice; one secondary source paraphrase states "large, complex applications with intricate dependency graphs and enterprise environments needing unified development standards suit Hilt," and another notes Hilt is Google-maintained/recommended while Koin is not — both are opinion-level claims, not benchmarked findings. — [search synthesis]

### Inferences
- A balanced interview answer: choose **Hilt/Dagger** when the project is Android-only, large/long-lived, has multiple teams needing enforced compile-time guarantees, or includes Java code; choose **Koin** when the project is (or may become) Kotlin Multiplatform, the team wants less setup/boilerplate and faster onboarding, or the app's DI graph is simple/moderate enough that Koin's growing compile-time tooling (Compiler Plugin, `verify()`) provides "enough" safety without Dagger's ceremony.
- Because Koin's own compile-time plugin is relatively new and has documented scope limits (doesn't cover runtime-loaded/library-only modules, dynamic `if`/`when` module lists), teams with very large graphs and zero tolerance for runtime DI failures still have a legitimate reason to prefer Dagger/Hilt's unconditional compile-time verification — this is a defensible, non-dogmatic answer to give in an interview rather than picking a "winner."

### Gaps
- Did not find a rigorous, methodologically transparent, independently reproduced build-time benchmark comparing Koin vs. Hilt at varying project/module scale — all build-time claims found are either vendor-page qualitative statements or blog-level anecdotes, and they don't fully agree with each other. This should be flagged in the interview-prep report as "disputed/anecdotal, verify on your own project" rather than cited as settled numbers.
- No authoritative, quantifiable guidance was found on a "team size" threshold for choosing one framework over the other; treat any such claim in the report as practitioner opinion only.

---

## Cross-cutting notes on source reliability for the report writer

- **Highest-confidence sources used**: official Koin documentation (insert-koin.io) pages for Core Concepts, Android Scope, Android Best Practices, Qualifiers, verify()/checkModules(), Context Isolation, Koin vs Hilt/Dagger, and Troubleshooting — these were fetched/queried directly and should be treated as primary.
- **Medium-confidence sources**: Kotlin Slack chat threads (surfaced via search, not directly opened) reporting specific exception messages and maintainer comments (e.g., the note that a cycle detector existed in Koin 2.1.x and was removed) — plausible and specific, but not independently verified against Koin's changelog/source in this pass.
- **Lower-confidence sources**: Medium/dev.to/droidcon blog posts and Reddit/forum threads on the service-locator debate and Koin-vs-Hilt performance — useful for capturing *practitioner sentiment and realistic interview framing*, but several of these directly contradict each other (especially on relative performance), so the report should present those as disputed opinion rather than settled fact.
- One frequently searched term, `NoBeanDefFoundException`, does not appear to be the current, accurate Koin exception name — current docs use `NoDefinitionFoundException`. Worth flagging explicitly in the final report as a common inaccurate/legacy term so the report doesn't repeat a wrong API name as fact.
