# Koin DSL Building Blocks (Koin 4.x)

## `module { }` DSL: declaration, loading, lazy modules

### Takeaway
`module { }` is a builder function that produces a `Module` object holding a set of definitions; modules are composed (via `includes` or `+`) and registered with Koin either eagerly through `startKoin { modules(...) }` / `koinApplication { modules(...) }`, or lazily (loaded on a background coroutine) through `lazyModule { }` + `lazyModules(...)`.

### Cited Findings
- `module { }` declares a module; `module(createdAtStart = true) { }` forces every `single` declared inside it to be created eagerly when Koin starts — [DSL reference](https://insert-koin.io/docs/reference/koin-core/dsl)
- `includes(otherModule, ...)` composes modules together (accepts varargs or a `Collection<Module>`); `moduleA + moduleB` builds a `List<Module>` — [DSL reference](https://insert-koin.io/docs/reference/koin-core/dsl)
- `lazyModule { }` declares a "Lazy Kotlin version of Koin Module" that is loaded later, in the background, and requires the `koin-core-coroutines` artifact — [DSL reference](https://insert-koin.io/docs/reference/koin-core/dsl); [Android modules reference](https://insert-koin.io/docs/reference/koin-android/modules-android)
- As of Koin 4.2.0, lazy modules load in parallel — each lazy module gets its own coroutine job; to wait for them you call `koin.waitAllStartJobs()` or use `Koin.runOnKoinStarted` — [Koin 4.2.0 release notes](https://newreleases.io/project/github/InsertKoinIO/koin/release/4.2.0)
- `startKoin { }` creates a `KoinApplication`, registers it in the global context (`GlobalContext`), and throws `KoinApplicationAlreadyStartedException` if one is already started; it can only be called once per process — [Starting Koin](https://insert-koin.io/docs/reference/koin-core/starting-koin); [DSL reference](https://insert-koin.io/docs/reference/koin-core/dsl)
- Inside `startKoin { }`/`koinApplication { }`: `modules(...)` accepts a single `Module`, a vararg of modules, or a `List<Module>`; `lazyModules(...)` loads lazy modules in the background; `createEagerInstances()` creates every `createdAtStart` definition (both `startKoin` and `koinApplication` already call this automatically); `allowOverride(Boolean)` defaults to `true` — [DSL reference](https://insert-koin.io/docs/reference/koin-core/dsl)
- Example usage: `startKoin { androidLogger(); androidContext(this@MainApplication); modules(appModule) }` — [Android start reference](https://insert-koin.io/docs/reference/koin-android/start)
- Module definitions are mutable objects; sharing one `val module = module {...}` across two isolated `koinApplication` instances can leak/corrupt state between them — build modules via a function call rather than a shared `val` when you need multiple independent instances (e.g., parallel tests) — [Kotlinlang Slack thread](https://slack-chats.kotlinlang.org/t/499717/i-m-trying-to-run-my-test-suite-which-uses-koin-multithreade); [Koin Puzzler blog post](https://ruslan.ibragimov.by/2024/08/11/koin-puzzler/)

### Inferences
- "Lazy module loading" in Koin refers specifically to deferring the *construction/registration* of a whole module to a background coroutine (useful for large apps wanting faster `Application.onCreate()`), not to laziness of individual `single`/`factory` instances (those are already lazy unless `createdAtStart` is set).
- `createdAtStart` at the module level (`module(createdAtStart = true) { }`) is a convenience that implicitly marks every `single` inside as eager, equivalent to adding `createdAtStart()` to each definition individually.

### Gaps
- No explicit code sample found for calling `lazyModule { }` itself (only its existence and parallel-loading behavior in 4.2.0 is documented); exact syntax for consuming a `lazyModule` value (e.g., whether it's passed to `lazyModules(...)` the same way `modules(...)` takes `Module`) was not directly quoted from a primary source.

---

## `single { }` vs `factory { }` vs `scoped { }`: lifecycle semantics

### Takeaway
`single` creates exactly one instance for the life of the Koin container (or isolated `koinApplication`) and reuses it on every resolution; `factory` creates a brand-new instance on every single `get()`/injection call; `scoped` creates one instance per *scope instance* (not per container), shared while that scope is open and discarded when the scope closes.

### Cited Findings
- Koin lists five definition kinds: `single`, `factory`, `scoped`, `viewModel`, and `worker` — [Definitions reference](https://insert-koin.io/docs/reference/koin-core/definitions/)
- Classic DSL forms, all equivalent in constructor-wiring style:
  ```kotlin
  val appModule = module {
      single { Database() }
      single { UserRepository(get()) }
      factory { UserPresenter(get()) }
      viewModel { UserViewModel(get()) }
  }
  ```
  — [Definitions reference](https://insert-koin.io/docs/reference/koin-core/definitions/)
- Constructor DSL (autowired, no manual `get()` calls) equivalent: `singleOf(::Database)`, `singleOf(::UserRepository)`, `factoryOf(::UserPresenter)`, `viewModelOf(::UserViewModel)` — [Definitions reference](https://insert-koin.io/docs/reference/koin-core/definitions/)
- `single(qualifier, createdAtStart) { }` — `qualifier` defaults to `null`, `createdAtStart` defaults to `false`; `factory(qualifier) { }` and `scoped(qualifier) { }` also take an optional `qualifier` defaulting to `null` — [DSL reference](https://insert-koin.io/docs/reference/koin-core/dsl)
- `scoped` requires a scope declaration — it must live inside a `scope<T> { }` or `scope(named(...)) { }` block, e.g. `scope<UserSession> { scoped<SessionData>(); factory<CheckoutPresenter>() }` — [Scopes reference](https://insert-koin.io/docs/reference/koin-core/scopes)
- A `scoped` definition yields one instance per scope *instance* — i.e., if you create two separate `UserSession` scope instances, each gets its own `SessionData`, but within one scope instance, repeated resolution returns the same `SessionData` — [Scopes reference](https://insert-koin.io/docs/reference/koin-core/scopes)
- A `factory` declared *inside* a scope block still creates a new instance per request, but it is permitted to depend on that scope's `scoped` instances — [Scopes reference](https://insert-koin.io/docs/reference/koin-core/scopes)
- `onClose { }` (or, in Compiler Plugin DSL, `scoped<SessionData>() onClose { it?.clear() }`) sets a release callback invoked when the owning scope instance closes — [DSL reference](https://insert-koin.io/docs/reference/koin-core/dsl); [Scopes reference](https://insert-koin.io/docs/reference/koin-core/scopes)
- `createdAtStart()` marks a `single` to be instantiated immediately when Koin starts rather than on first resolution; eager creation runs on the thread that calls `startKoin`, so on Android it adds to `Application.onCreate()` time — [Definitions reference](https://insert-koin.io/docs/reference/koin-core/definitions/)
- Annotation equivalents: `@Singleton` (recommended, JSR-330-aligned) and `@Single` are documented as equivalent; `@Factory`; `@Scope(UserSession::class)` + `@Scoped` — [Annotations inventory](https://insert-koin.io/docs/reference/koin-annotations/annotations-inventory)

### Inferences
- The three keywords map directly onto classic DI lifecycle terms: `single` = singleton/application scope, `factory` = prototype/transient, `scoped` = a user-defined intermediate lifecycle (e.g., per-session, per-screen, per-request) that you explicitly open and close.
- Because `scoped` needs an enclosing `scope { }` block while `single`/`factory` do not, `scoped` is structurally distinguishable at the DSL level — it cannot be declared loose at module top level.

### Gaps
- No primary-source code example was retrieved showing `scopedOf(::X)` (the constructor-DSL equivalent for `scoped`) even though `singleOf`/`factoryOf`/`viewModelOf` were documented; its existence is plausible by naming convention but unconfirmed in the fetched pages.

---

## `bind<Interface>()` and multiple bindings vs declaring the return type

### Takeaway
Koin binds a definition to the concrete type (and, with the Compiler Plugin, to all supertypes automatically); `bind`/`binds` add one or more *additional* types the same instance can be resolved as, while declaring the return type directly (e.g., `single<UserRepository> { UserRepositoryImpl(get()) }`) makes the interface itself the primary/only resolvable type.

### Cited Findings
- Compiler Plugin DSL: `single<UserRepositoryImpl>() bind UserRepository::class` for one extra type, or `single<MyServiceImpl>() binds arrayOf(ServiceA::class, ServiceB::class)` for several — [Definitions reference](https://insert-koin.io/docs/reference/koin-core/definitions/)
- Annotation equivalent: `@Singleton(binds = [UserRepository::class]) class UserRepositoryImpl(...) : UserRepository, Closeable` — [Definitions reference](https://insert-koin.io/docs/reference/koin-core/definitions/)
- Constructor DSL: `singleOf(::UserRepositoryImpl) bind UserRepository::class`, or alternatively declare the interface as the generic type directly: `single<UserRepository> { UserRepositoryImpl(get()) }` — [Definitions reference](https://insert-koin.io/docs/reference/koin-core/definitions/)
- "Without `binds`, the class is bound to all its supertypes automatically" (this applies under the Compiler Plugin) — [Definitions reference](https://insert-koin.io/docs/reference/koin-core/definitions/)
- In the manual/classic DSL form shown on the DSL reference page: `bind` adds a secondary type; `binds` adds several, e.g. `binds(listOf(I::class, J::class))` — [DSL reference](https://insert-koin.io/docs/reference/koin-core/dsl)
- Combined example from the DSL reference showing `withOptions` plus Constructor DSL side by side:
  ```kotlin
  val appModule = module {
      single<UserRepositoryImpl>() withOptions {
          named("local")
          bind<UserRepository>()
          createdAtStart()
      }
      // Constructor DSL
      singleOf(::UserRepositoryImpl) {
          named("local")
          bind<UserRepository>()
      }
  }
  ```
  — [DSL reference](https://insert-koin.io/docs/reference/koin-core/dsl)

### Inferences
- "Declaring the return type" (`single<UserRepository> { UserRepositoryImpl(get()) }`) and "`bind<UserRepository>()`" are functionally different entry points to the same goal: the former makes `UserRepository` the *only* type Koin knows the definition by (callers must request `UserRepository`, not `UserRepositoryImpl`), whereas `bind`/`binds` keeps the concrete type as a valid request type *and* adds the interface(s) as additional valid request types.
- This matters for `get<ConcreteImpl>()` calls: if you only declare `single<Interface> { Impl() }`, resolving `Impl` directly will fail; if you use `bind<Interface>()` on a `single<Impl>()`/`singleOf(::Impl)`, both `Impl` and `Interface` resolve to the same instance.

### Gaps
- None significant — this topic was well covered by two independent official pages (DSL reference and Definitions reference) with consistent, non-conflicting information.

---

## Qualifiers: `named("x")`, type qualifiers, enum qualifiers

### Takeaway
A qualifier disambiguates multiple definitions of the same type; Koin supports string qualifiers (`named("x")`), type qualifiers (`named<Type>()`, aliased as `qualifier<Type>()`), and enum qualifiers (`named(EnumValue)`), and the qualifier used at declaration time must exactly match the one used at the injection site.

### Cited Findings
- Problem statement: "Koin otherwise resolves by type alone, so two bindings of `Database` would be ambiguous without one [a qualifier]" — [Qualifiers reference](https://insert-koin.io/docs/reference/koin-core/qualifiers)
- String qualifier example:
  ```kotlin
  val dataModule = module {
      single<Database>(named("local")) { LocalDatabase() }
      single<Database>(named("remote")) { RemoteDatabase() }
      single {
          UserRepository(
              localDb = get(named("local")),
              remoteDb = get(named("remote")),
          )
      }
  }
  ```
  — [Qualifiers reference](https://insert-koin.io/docs/reference/koin-core/qualifiers)
- Retrieval with string qualifiers: `val localDb: Database by inject(named("local"))` and `val remoteDb: Database = get(named("remote"))`; the same qualifier argument works with Compose's `koinInject(named("local"))` — [Qualifiers reference](https://insert-koin.io/docs/reference/koin-core/qualifiers)
- `singleOf(::UserRepository)` (constructor DSL) resolves parameters by type only and **cannot** pass qualifiers — you must fall back to an explicit `single { UserRepository(get(named(...))) }` when a dependency needs a qualifier — [Qualifiers reference](https://insert-koin.io/docs/reference/koin-core/qualifiers)
- Type qualifier example: `named<LocalDb>()` / `named<RemoteDb>()` using marker `object` types; typos become compile errors and IDE renames propagate; `qualifier<LocalDb>()` is documented as an alias of `named<LocalDb>()` — [Qualifiers reference](https://insert-koin.io/docs/reference/koin-core/qualifiers)
- Enum qualifier example:
  ```kotlin
  enum class DbType { LOCAL, REMOTE }
  val dataModule = module {
      single<Database>(named(DbType.LOCAL)) { LocalDatabase() }
      single<Database>(named(DbType.REMOTE)) { RemoteDatabase() }
  }
  val localDb: Database = get(named(DbType.LOCAL))
  ```
  `named(DbType.LOCAL)` is documented as being "the same qualifier as `named("local")`, built from the lowercase entry name" — [Qualifiers reference](https://insert-koin.io/docs/reference/koin-core/qualifiers)
- Documented pitfalls: (1) if every definition of a type is qualified and you call plain `get<Database>()`, it throws `NoDefinitionFoundException`; (2) string-qualifier typos (`named("encrypted")` vs `named("encrypted_client")`) silently create two different, unmatched qualifiers — type/enum qualifiers or shared string constants avoid this; (3) adding a qualifier is unnecessary noise when the types already differ — [Qualifiers reference](https://insert-koin.io/docs/reference/koin-core/qualifiers)
- Annotation equivalent: `@Named("local")` on the class declaration and `@Named("local")` on the constructor parameter requesting it; `jakarta.inject.Named`/`javax.inject.Named` also work. A more general `@Qualifier(Remote::class)` form also exists and can mark custom qualifier annotations — [Annotations inventory](https://insert-koin.io/docs/reference/koin-annotations/annotations-inventory)

### Inferences
- Enum qualifiers are not a distinct qualifier *type* in the API — they are string qualifiers under the hood, generated from the enum entry's name in lowercase, so `named(DbType.LOCAL)` and `named("local")` are interchangeable/colliding qualifiers (a subtle gotcha if you mix styles).

### Gaps
- None material — qualifier semantics were documented clearly and consistently across the dedicated Qualifiers page and the Annotations inventory page.

---

## Parameters: `parametersOf(...)` and runtime injected parameters

### Takeaway
`parametersOf(...)` (and the lazy equivalent passed to `inject { }`) lets a caller pass runtime values into a definition lambda at resolution time; those values are read inside the lambda via the `params` argument (`params.get<T>()`, destructuring, or index access), and this mechanism is intended for `factory`/`scoped`-style definitions since a `single` only consumes parameters the first time it's created.

### Cited Findings
- Basic usage: `val presenter: UserPresenter = get { parametersOf("user-42") }` or lazily `val presenter: UserPresenter by inject { parametersOf("user-42") }`, matched positionally — [Injected parameters reference](https://insert-koin.io/docs/reference/koin-core/injection-parameters)
- "`parametersOf` is for `factory` definitions. A `single` keeps the first values it receives and ignores later ones." — [Injected parameters reference](https://insert-koin.io/docs/reference/koin-core/injection-parameters)
- Classic DSL reading parameters: `factory { params -> UserPresenter(userId = params.get(), repository = get()) }` — [Injected parameters reference](https://insert-koin.io/docs/reference/koin-core/injection-parameters)
- Destructuring form (limited to five values): `factory { (userId: String, orderId: Long) -> OrderPresenter(userId, orderId, get()) }` — [Injected parameters reference](https://insert-koin.io/docs/reference/koin-core/injection-parameters)
- Index-based access: `factory { params -> OrderPresenter(params[0], params[1], get()) }`, called as `get { parametersOf("user-42", 1001L) }` — [Injected parameters reference](https://insert-koin.io/docs/reference/koin-core/injection-parameters)
- Matching algorithm for `params.get<T>()`: (1) Koin first checks the value at the current position — if its type matches, it's returned and the position advances; (2) otherwise Koin returns the first value of the requested type anywhere in the list; `getOrNull<T>()` returns `null` on no match, `get<T>()` throws `DefinitionParameterException` — [Injected parameters reference](https://insert-koin.io/docs/reference/koin-core/injection-parameters)
- Alternate builders change the matching strategy: `parametersOf(...)` = position-first-then-type (default); `parameterArrayOf(...)` = position only; `parameterSetOf(...)` = type only. Example: `parameterArrayOf(1, 2, 3)` then two successive `.get<Int>()` calls return `1` then `2`; `parameterSetOf("a_string", 42).get<Int>()` returns `42` — [Injected parameters reference](https://insert-koin.io/docs/reference/koin-core/injection-parameters)
- Compiler Plugin / Annotation equivalent uses `@InjectedParam` on the constructor parameter so the Compiler Plugin doesn't flag it as an unresolved dependency:
  ```kotlin
  class UserPresenter(
      @InjectedParam val userId: String,
      val repository: UserRepository,
  )
  val appModule = module {
      single<UserRepository>()
      factory<UserPresenter>()
  }
  ```
  or with annotations: `@Factory class UserPresenter(@InjectedParam val userId: String, val repository: UserRepository)` — [Injected parameters reference](https://insert-koin.io/docs/reference/koin-core/injection-parameters)
- Warning: "if a passed value has the same type as the requested definition, Koin returns it directly and skips the definition. Wrap such values in their own type, such as a value class" — [Injected parameters reference](https://insert-koin.io/docs/reference/koin-core/injection-parameters)

### Inferences
- The `@InjectedParam`/Compiler-Plugin style and the classic `params.get()` style are two surfaces over the same runtime mechanism; the annotation form exists mainly so the build-time dependency-graph checker doesn't falsely report the parameter as missing from the graph.

### Gaps
- None significant for this question — the Injected Parameters reference page was comprehensive and internally consistent.

---

## `get()` vs `inject()` (property delegate) vs constructor injection via DSL

### Takeaway
`get<T>()` resolves a dependency immediately (eager, synchronous call returning `T`), `by inject<T>()` returns a `Lazy<T>` property delegate resolved on first access, and constructor-injection-via-DSL means writing `get()`/`get(named(...))` calls directly inside a `single { }`/`factory { }` lambda's constructor call so the dependency graph is wired explicitly at definition time rather than resolved ad hoc later.

### Cited Findings
- Side-by-side: `private val userService: UserService by inject()` (lazy, resolved on first access) vs `private val analytics: Analytics = get()` (eager, resolved immediately) — [Retrieving Dependencies reference](https://insert-koin.io/docs/reference/koin-core/injection)
- Table: `by inject<T>()` returns `Lazy<T>` used as a property delegate, resolved "on first access"; `get<T>()` returns `T`, resolved "immediately." Guidance: "Use `by inject()` for properties," use `get()` inside a function body when you need the value immediately — [Retrieving Dependencies reference](https://insert-koin.io/docs/reference/koin-core/injection)
- `inject()` takes a `mode` parameter (a Kotlin `LazyThreadSafetyMode`), defaulting to `SYNCHRONIZED` on the JVM — [Retrieving Dependencies reference](https://insert-koin.io/docs/reference/koin-core/injection)
- `get()`/`inject()` can be called from: Android framework classes (`Activity`, `Fragment`, `Service`) with no extra interface needed; any class implementing `KoinComponent`; or directly off a `Koin` instance, e.g. `KoinPlatform.getKoin().get<UserService>()` (global) or `koinApp.koin.get<UserService>()` (isolated instance) — [Retrieving Dependencies reference](https://insert-koin.io/docs/reference/koin-core/injection)
- Nullable variants: `KoinComponent` itself does not expose nullable `get`/`inject` — call `getKoin().getOrNull<T>()` instead; `KoinPlatform.getKoinOrNull()` returns `null` instead of throwing if Koin hasn't started; example: `val tracker: Tracker? = koin.getOrNull()`, `val crashReporter: CrashReporter? by koin.injectOrNull()` — [Retrieving Dependencies reference](https://insert-koin.io/docs/reference/koin-core/injection)
- Constructor-injection-in-DSL examples: `single { Database() }`, `single { UserRepository(get()) }`, `factory { UserPresenter(get()) }` — every `get()` call inside the lambda resolves one constructor argument — [Definitions reference](https://insert-koin.io/docs/reference/koin-core/definitions/)
- With qualifiers inline: `UserRepository(localDb = get(named("local")), remoteDb = get(named("remote")))` — [Qualifiers reference](https://insert-koin.io/docs/reference/koin-core/qualifiers)
- The Constructor DSL (`singleOf(::UserRepository)`, etc.) is explicitly an alternative to writing `get()` calls by hand — it autowires constructor parameters purely by type, which is why it cannot carry qualifiers — [Definitions reference](https://insert-koin.io/docs/reference/koin-core/definitions/); [Qualifiers reference](https://insert-koin.io/docs/reference/koin-core/qualifiers)

### Inferences
- The three "injection styles" the task names are really two axes: *where* resolution happens (inside a module definition lambda vs. at an arbitrary call site/property) and *when* it happens (eager `get()` vs lazy `by inject()`). Constructor injection via DSL is just `get()` used specifically inside a definition lambda's constructor call.

### Gaps
- None significant.

---

## Scopes: `scope { scoped { } }`, creating/closing, the `Scope` object, Activity/Fragment patterns

### Takeaway
A Koin scope is a named/typed lifecycle container you open explicitly (`createScope`), resolve `scoped` definitions from while it's open, and close explicitly (`scope.close()`) to release them; on Android, `activityScope { }` / `fragmentScope { }` are pre-built "scope archetypes" that tie this lifecycle to the Activity/Fragment automatically.

### Cited Findings
- Declaring a scope and its contents:
  ```kotlin
  scope<UserSession> {
      scoped<SessionData>()
      factory<CheckoutPresenter>()
  }
  scope(named("checkout")) { scoped<CheckoutNavigator>() }
  ```
  Use a type (`scope<T>`) when the scope ties to one class, and a qualifier/name (`scope(named(...))`) when no single class fits — [Scopes reference](https://insert-koin.io/docs/reference/koin-core/scopes)
- `scoped<SessionData>() onClose { it?.clear() }` — the `onClose` callback fires when that scope instance closes — [Scopes reference](https://insert-koin.io/docs/reference/koin-core/scopes)
- Creating/closing programmatically:
  ```kotlin
  val scope = koin.createScope<UserSession>(userId)
  val cart: CartRepository = scope.get()
  koin.getScopeOrNull(userId)?.close()
  ```
  — [Scopes reference](https://insert-koin.io/docs/reference/koin-core/scopes)
- Scope lookup/management functions on `Koin`: `createScope<T>(scopeId)`, `createScope(scopeId, qualifier)`, `getOrCreateScope(scopeId, qualifier)`, `getScope(scopeId)` (throws `ScopeNotCreatedException` if none exists), `getScopeOrNull(scopeId)`, `deleteScope(scopeId)` — [Scopes reference](https://insert-koin.io/docs/reference/koin-core/scopes)
- Scope ids must be unique among currently open scopes; closing a scope runs `onClose` callbacks and removes it from the registry; subsequent `get()` calls on a closed scope throw `ClosedScopeException`; a closed scope cannot be reopened — [Scopes reference](https://insert-koin.io/docs/reference/koin-core/scopes)
- `Scope` object methods: `get()` resolves an instance from that scope; `declare(instance)` adds an already-built object into the scope instance; `linkTo(scope)` / `unlink(scope)` add/remove another scope from the resolution chain; `close()` closes it; `closed` reports whether it's closed; `registerCallback()` runs code on close — [Scopes reference](https://insert-koin.io/docs/reference/koin-core/scopes)
- `KoinScopeComponent` pattern for tying a scope to an arbitrary class:
  ```kotlin
  class UserSession : KoinScopeComponent {
      override val scope: Scope by lazy { createScope(this) }
      val cart: CartRepository by inject()
  }
  ```
  — [Scopes reference](https://insert-koin.io/docs/reference/koin-core/scopes)
- Scope linking/resolution order: linking (`checkoutScope.linkTo(sessionScope)`) lets `checkoutScope.get()` resolve a type only declared in `sessionScope`; "Resolution searches the current scope, then its linked scopes in order, and the root scope last. The root scope cannot be linked to another scope." — [Scopes reference](https://insert-koin.io/docs/reference/koin-core/scopes)
- Android scope archetypes: `activityScope { scoped<ActivityPresenter>() }` and `fragmentScope { scoped<FragmentPresenter>() }`; annotation equivalents are `@ActivityScope` and `@FragmentScope`; these helpers "close the scope automatically when the Activity or Fragment is destroyed" — [Scopes reference](https://insert-koin.io/docs/reference/koin-core/scopes)
- Annotation-driven scopes also include `@ActivityRetainedScope` (survives configuration changes, Android-only) and `@ViewModelScope` (scopes a definition to a ViewModel's lifetime, no Android dependency) and `@ScopeId(name = "...")` to resolve a constructor parameter from a named scope other than the current one — [Annotations inventory](https://insert-koin.io/docs/reference/koin-annotations/annotations-inventory)
- Compose has a distinct, separately documented `KoinScope` composable (`KoinScope(scopeOf<FeatureScope>())`) that binds scope lifetime to a composable; the **official page explicitly warns the scope it creates is NOT automatically closed** — the developer must manage closing it. A third-party mirror (Tessl, labeled Koin 4.1.0) describes a variant that *does* auto-close on composition forgotten/abandoned — this directly **contradicts** the official page, so the researcher flags it as an unresolved conflict pending version confirmation — [Scopes in Compose (official)](https://insert-koin.io/docs/reference/koin-compose/compose-scopes) vs [Tessl mirror, v4.1.0](https://tessl.io/registry/tessl/maven-io-insert-koin--koin-compose-jvm/4.1.0/files/docs/scope-management.md)

### Inferences
- "`koinScope`" as named in the task brief does not appear verbatim as a distinct top-level API in the fetched official pages; the closest real constructs are `scope<T> { }` (DSL declaration), `Scope` (the resolved object), `KoinScopeComponent.scope` (the property name convention used when a class owns a scope), and Compose's `KoinScope(...)` composable. Readers building diagrams should use `Scope`/`KoinScopeComponent`/`activityScope`/`fragmentScope`/Compose's `KoinScope` as the canonical names rather than a generic "koinScope" symbol.
- The Activity/Fragment scope pattern is essentially `KoinScopeComponent` pre-wired by the `koin-android` artifact: `activityScope`/`fragmentScope` save you from manually calling `createScope`/`close()` in `onCreate`/`onDestroy`.

### Gaps
- Exact lifecycle hook names used internally by `activityScope`/`fragmentScope` (e.g., which Activity/Fragment lifecycle callback triggers the auto-close) were not found in the fetched excerpt — the Scopes reference points to a separate "Android Scopes" page for details that wasn't independently fetched.
- The Compose `KoinScope` auto-close behavior conflict (official docs: manual close required; third-party mirror: auto-closes) could not be resolved from the sources gathered; treat the official insert-koin.io page as authoritative unless a specific Koin version is confirmed to change this.

---

## Android-specific helpers: `viewModel { }` / `koinViewModel()`, `androidContext()`, `androidLogger()`, `androidScope()`, WorkManager

### Takeaway
Koin's Android layer adds `viewModel { }`/`viewModelOf(::X)` module definitions plus `by viewModel()` (Activity/Fragment) and `koinViewModel()` (Compose) retrieval functions, `androidContext()`/`androidApplication()`/`androidLogger()`/`androidFileProperties()` setup helpers inside `startKoin { }`, pre-built Activity/Fragment scope helpers (no separate "`androidScope()`" function was found — see Gaps), and a dedicated `koin-androidx-workmanager` artifact exposing `workManagerFactory()` plus `worker { }`/`workerOf(::X)`/`@KoinWorker`.

### Cited Findings
- ViewModel declaration (classic): `viewModel { UserViewModel(get()) }`; constructor DSL: `viewModelOf(::UserViewModel)`, recommended because it "doesn't keep any instance in memory (avoid any leak with Android lifecycle)" — [Definitions reference](https://insert-koin.io/docs/reference/koin-core/definitions/); [Android ViewModel quickstart](https://insert-koin.io/docs/quickstart/android-viewmodel)
- Activity retrieval: `private val viewModel: UserViewModel by viewModel()` (lazy) imported from `org.koin.androidx.viewmodel.ext.android.viewModel`; eager alternative `val sameViewModel: UserViewModel = getViewModel()` — [Android ViewModel reference](https://insert-koin.io/docs/reference/koin-android/viewmodel)
- Fragment retrieval, including Activity-shared ViewModels: `private val detailViewModel: UserDetailViewModel by viewModel()` (owned by the Fragment) vs `private val userViewModel: UserViewModel by activityViewModel()` (owned by the host Activity, shared across its Fragments) — [Android ViewModel reference](https://insert-koin.io/docs/reference/koin-android/viewmodel)
- Parameterized/qualified retrieval: `by viewModel { parametersOf(itemId) }`, `by viewModel(qualifier = named("draft"))`; child-Fragment sharing: `by viewModel(ownerProducer = { requireParentFragment() })`; nav-graph scoped: `by koinNavGraphViewModel(R.id.checkout_graph)` from `io.insert-koin:koin-androidx-navigation`; Java interop: `ViewModelCompat.viewModel(this, UserViewModel.class)` from `io.insert-koin:koin-android-compat` — [Android ViewModel reference](https://insert-koin.io/docs/reference/koin-android/viewmodel)
- **Deprecated/incorrect usage flagged by the docs:** `stateViewModel()` has been removed — use `viewModel()` instead. Retrieving a ViewModel with plain `by inject()` or `get()` is explicitly called *wrong* (not merely deprecated) because "each call creates a new, unmanaged instance" outside the ViewModel lifecycle machinery. Injecting Activity-/Fragment-scoped instances directly into a ViewModel is warned against because it can leak the old Activity after a configuration change/rotation — [Android ViewModel reference](https://insert-koin.io/docs/reference/koin-android/viewmodel)
- Compose: `koinViewModel()` "retrieves a ViewModel instance, creates the associated ViewModel Factory for you, and binds it to the lifecycle"; common pattern: `viewModel: UserViewModel = koinViewModel()` as a composable default parameter; requires the `koin-androidx-compose` artifact — [Reference/quickstart on Compose ViewModels, as summarized from search](https://insert-koin.io/docs/reference/koin-core/viewmodel); [Android Compose quickstart](https://insert-koin.io/docs/quickstart/android-compose)
- **Deprecation in Compose setup:** In the Koin 4.1 docs, `KoinContext` and `KoinAndroidContext` composables are deprecated. Current guidance: call `startKoin` before your Compose application starts, and nothing else is required to wire Compose to the Koin context. Alternatively, a `KoinApplication` composable starts/stops an isolated Koin context following the Compose context's own lifecycle. The Koin 4.0 docs still show `KoinContext()` as current — so `KoinContext()`/`KoinAndroidContext()` are the *old/deprecated* pattern as of 4.1, superseded by calling `startKoin` earlier (e.g. in `Application.onCreate`) or using the `KoinApplication` composable — [4.0 Compose reference](https://insert-koin.io/docs/4.0/reference/koin-compose/compose) vs [4.1 Compose reference](https://insert-koin.io/docs/4.1/reference/koin-compose/compose)
- `androidContext()`: adds the `Application` instance to Koin, bound to both `Application` and `Context`; must be called before any option needing a `Context` (e.g., `androidFileProperties()`, `workManagerFactory()`); inside definition lambdas it resolves to "the closest `Context`" — the Activity if inside an Activity scope, otherwise the `Application`; throws `MissingAndroidContextException` if `androidContext()` wasn't supplied at start; it's a shortcut for `get<Context>()` — [Android start reference](https://insert-koin.io/docs/reference/koin-android/start)
- Explicit warning: "Don't declare an `Activity` as a `single` dependency, because the singleton keeps it alive after destruction and leaks it." — [Android start reference](https://insert-koin.io/docs/reference/koin-android/start)
- `androidApplication()`: always returns the `Application` instance; shortcut for `get<Application>()`; fails if no `Application` was given to `androidContext()` — [Android start reference](https://insert-koin.io/docs/reference/koin-android/start)
- `androidLogger()`: routes Koin logs to Logcat, default level `Level.INFO`; `Level.DEBUG` logs every resolution and its duration (described as slowing resolution, so "use it for debug builds only"); pattern: `androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.ERROR)` — [Android start reference](https://insert-koin.io/docs/reference/koin-android/start)
- `androidFileProperties()`: loads Koin properties from `src/main/assets/koin.properties` by default (custom filename supported, e.g. `androidFileProperties("app.properties")`); must be called after `androidContext()` since it reads through `Context`; logs and continues if the file is missing — [Android start reference](https://insert-koin.io/docs/reference/koin-android/start)
- Full classic Application setup:
  ```kotlin
  class MainApplication : Application() {
      override fun onCreate() {
          super.onCreate()
          startKoin {
              androidLogger()
              androidContext(this@MainApplication)
              modules(appModule)
          }
      }
  }
  ```
  — [Android start reference](https://insert-koin.io/docs/reference/koin-android/start)
- Compiler Plugin DSL Application setup differs structurally — uses `@KoinApplication` on the `Application` class and a typed `startKoin<MainApplication> { ... }` call (modules are discovered via `@Configuration`-tagged `@Module` classes, or restricted explicitly with `@KoinApplication(modules = [AppModule::class])`):
  ```kotlin
  @KoinApplication
  class MainApplication : Application() {
      override fun onCreate() {
          super.onCreate()
          startKoin<MainApplication> {
              androidLogger()
              androidContext(this@MainApplication)
          }
      }
  }
  ```
  — [Android start reference](https://insert-koin.io/docs/reference/koin-android/start)
- Manifest requirement: the Application subclass must be registered via `android:name=".MainApplication"` on `<application>` or Android never runs it; `startKoin` must run once per process, before the first injection, using `context.applicationContext` (never an Activity) — libraries should use an isolated context instead — [Android start reference](https://insert-koin.io/docs/reference/koin-android/start)
- `KoinStartup` (artifact `io.insert-koin:koin-androidx-startup`, package `org.koin.androix.startup`, marked `@KoinExperimentalAPI`) can replace the manual `startKoin` call in `Application.onCreate`, but "still runs on the main thread, so it does not speed up startup" — [Android start reference](https://insert-koin.io/docs/reference/koin-android/start)
- WorkManager support — dependency: `io.insert-koin:koin-androidx-workmanager` alongside `koin-android`; setup: call `workManagerFactory()` inside `startKoin`, after `androidContext()`:
  ```kotlin
  startKoin {
      androidContext(this@MainApplication)
      workManagerFactory()
      modules(appModule)
  }
  ```
  `workManagerFactory()` "initializes WorkManager with a configuration that uses the Koin `WorkerFactory`" and "does nothing if WorkManager is already initialized" — which happens if the default AndroidX initializer runs first — [WorkManager reference](https://insert-koin.io/docs/reference/koin-android/workmanager)
- Must also remove AndroidX's default WorkManager initializer from `AndroidManifest.xml` via a `tools:node="remove"` meta-data override, otherwise Workers with extra constructor parameters fail at runtime — [WorkManager reference](https://insert-koin.io/docs/reference/koin-android/workmanager)
- Worker registration, three equivalent styles — Compiler Plugin DSL: `worker<SyncWorker>()`; Annotations: `@KoinWorker class SyncWorker(...)`; Classic/Constructor DSL: `workerOf(::SyncWorker)` or manual `worker { params -> SyncWorker(androidContext(), params.get(), get()) }` — [WorkManager reference](https://insert-koin.io/docs/reference/koin-android/workmanager)
- Each worker definition becomes a factory named after the Worker class, bound to `ListenableWorker`; if no definition matches the requested class, Koin returns `null` and WorkManager falls back to its default factory. Per-run input (e.g., a user ID) should go through WorkManager's own `inputData`/`workDataOf(...)`, not Koin parameters, "WorkManager stores input data, so it survives process death. Koin parameters do not." — [WorkManager reference](https://insert-koin.io/docs/reference/koin-android/workmanager)

### Inferences
- No official API literally named `androidScope()` was found in any fetched page. The task's phrase likely refers collectively to the Activity/Fragment scope machinery documented as `activityScope { }` / `fragmentScope { }` (DSL) and `@ActivityScope` / `@FragmentScope` / `@ActivityRetainedScope` (annotations) under the Scopes reference — see the Scopes section above for exact syntax.
- The deprecation of `KoinContext()`/`KoinAndroidContext()` in Compose (4.1) in favor of calling `startKoin` earlier, or using the `KoinApplication` composable, is a concrete example of "older deprecated API to flag" that the task explicitly asked researchers to surface.

### Gaps
- No standalone function named `androidScope()` was confirmed to exist; flagging this as a likely terminology mismatch in the original task brief rather than a missing fact.
- Did not independently fetch the dedicated "Android Scopes" sub-page (only referenced by the core Scopes page), so finer lifecycle-hook details for `activityScope`/`fragmentScope` auto-close timing are unconfirmed.

---

## `KoinComponent` and `KoinScopeComponent`: opting in without constructor injection

### Takeaway
`KoinComponent` is a marker interface that unlocks `get()`/`by inject()`/`getKoin()` (plus `getProperty()`/`setProperty()`) on any class that Koin doesn't construct for you (so constructor injection isn't possible), while `KoinScopeComponent` is the scope-aware variant used when a class should own and resolve from its own `Scope` instance (e.g., `UserSession`); both are explicitly recommended only as a fallback, not as the default DI style.

### Cited Findings
- "`KoinComponent` is an interface that gives any class access to the Koin container without constructor injection. The page recommends it only for classes you can't declare in modules, and says to prefer constructor injection when possible." — [Koin Component reference (LTS mirror)](https://lts.insert-koin.io/docs/reference/koin-core/koin-component)
- Implementation pattern:
  ```kotlin
  class MyComponent : KoinComponent {
      // Lazy: instance retrieved when first accessed
      val myService: MyService by inject()
      // Eager: instance retrieved immediately
      val myService: MyService = get()
  }
  ```
  — [Koin Component reference](https://lts.insert-koin.io/docs/reference/koin-core/koin-component)
- Available members on `KoinComponent`: `get<T>()` (eager retrieval), `by inject<T>()` (lazy delegate), `getProperty()` / `setProperty()` (configuration properties), `getKoin()` (access to the underlying `Koin` instance) — [Koin Component reference](https://lts.insert-koin.io/docs/reference/koin-core/koin-component)
- Advanced usage via `getKoin()`:
  ```kotlin
  class AdvancedComponent : KoinComponent {
      fun performComplexOperation() {
          val koin = getKoin()
          val scope = koin.createScope<MyActivity>()
          val hasService = koin.getOrNull<MyService>() != null
          val allServices = koin.getAll<Service>()
      }
  }
  ```
  — [Koin Component reference](https://lts.insert-koin.io/docs/reference/koin-core/koin-component)
- Properties set via `setProperty()` are available to all components, persist for the Koin instance's lifetime, and are reset when `stopKoin()` is called — [Koin Component reference](https://lts.insert-koin.io/docs/reference/koin-core/koin-component)
- Guidance: "Android Activities, Fragments, and Services don't need `KoinComponent`; they use Koin's Android extensions instead." For tests: start Koin with a mock module and call `stopKoin()` during cleanup — [Koin Component reference](https://lts.insert-koin.io/docs/reference/koin-core/koin-component)
- `KoinScopeComponent` pattern (documented on the Scopes page, not the Koin Component page):
  ```kotlin
  class UserSession : KoinScopeComponent {
      override val scope: Scope by lazy { createScope(this) }
      val cart: CartRepository by inject()
  }
  ```
  — [Scopes reference](https://insert-koin.io/docs/reference/koin-core/scopes)
- General guidance against overusing either interface: the Dependency Injection Basics guide "recommends constructor injection instead, and reserves `KoinComponent` for cases like Android framework classes you don't construct yourself" — [Dependency Injection Basics guide, as summarized via search](https://insert-koin.io/docs/guides/dependency-injection-basics)

### Inferences
- `KoinScopeComponent` can be understood as `KoinComponent` plus one required override (`scope: Scope`) that ties `by inject()`/`get()` calls made on that class to a specific `Scope` instance instead of the global root scope — this is the idiomatic way to give a plain (non-Activity/Fragment) class its own scope lifecycle.

### Gaps
- A dedicated official page section fully describing `KoinScopeComponent`'s own member list (beyond the single example reused from the Scopes page) was not found/fetched directly — the one retrieved page that explicitly covers `KoinComponent` in depth states it does not cover `KoinScopeComponent` at all.

---

## `startKoin { }` vs `koinApplication { }`

### Takeaway
`startKoin { }` builds a `KoinApplication` and registers it in Koin's `GlobalContext` (one per process, used by all global APIs like `KoinComponent`, `by viewModel()`, `koinInject()`), whereas `koinApplication { }` builds a standalone, unregistered `KoinApplication` instance you access via its own `.koin` property — intended for isolated use cases like SDKs/libraries that must not pollute a host app's container, KMP multi-instance scenarios, or parallel/isolated tests.

### Cited Findings
- `startKoin { }` registers the container in `GlobalContext`, described as "the default context for your process"; after it returns, `get()`, `by inject()`, and platform APIs such as `by viewModel()` and `koinInject()` all resolve from this one container — [Starting Koin reference](https://insert-koin.io/docs/reference/koin-core/starting-koin)
- "You can call it only once per process. A second call throws `KoinApplicationAlreadyStartedException`." — [Starting Koin reference](https://insert-koin.io/docs/reference/koin-core/starting-koin); corroborated by [DSL reference](https://insert-koin.io/docs/reference/koin-core/dsl)
- `koinApplication { }` example:
  ```kotlin
  val koinApp = koinApplication {
      modules(sdkModule)
  }
  val service = koinApp.koin.get<SdkService>()
  ```
  "Global APIs (`KoinComponent`, `by inject()` in an Activity) don't see this container." Suggested for "libraries and SDKs that must not share the host app's container, or for tests that need their own instance." — [Starting Koin reference](https://insert-koin.io/docs/reference/koin-core/starting-koin)
- Typed variants requiring the Compiler Plugin: `startKoin<MyApp>()` (global, using `MyApp`'s declared modules), `koinApplication<MyApp>()` (isolated, not global), `koinConfiguration<MyApp>()` (builds configuration only, starts nothing), `withConfiguration<MyApp>()` (adds `MyApp`'s modules into an existing `KoinApplication`); "Without the plugin, these functions throw at runtime." — [Starting Koin reference](https://insert-koin.io/docs/reference/koin-core/starting-koin)
- `koinConfiguration { }` "builds a configuration without starting anything," meant to be passed into APIs that start Koin themselves, such as the Compose `KoinApplication` composable and the Ktor plugin — [Starting Koin reference](https://insert-koin.io/docs/reference/koin-core/starting-koin)
- Stopping/closing: `stopKoin()` for the global container — "releases every instance, calls the `onClose` callbacks of your definitions, and clears `GlobalContext`. You can then call `startKoin` again." `koinApp.close()` for an isolated `koinApplication` instance; "The article recommends stopping Koin after each test so the next one starts clean." — [Starting Koin reference](https://insert-koin.io/docs/reference/koin-core/starting-koin)
- Summary table from the source (reproduced): registered in `GlobalContext` — Yes for `startKoin`, No for `koinApplication`; visible to `by inject()`/`KoinComponent` — Yes / No; limit — one per process / multiple instances allowed; stop with — `stopKoin()` / `close()`; typical use — application startup / SDKs, libraries, isolated tests — [Starting Koin reference](https://insert-koin.io/docs/reference/koin-core/starting-koin)
- Caveat found independently: Koin modules are mutable objects, so sharing one `val module = module {...}` across two `koinApplication` isolated instances can let one instance's close/mutation affect the other; one developer's fix was to build the module via a function call instead of a shared top-level `val` for parallel test isolation — "Koin can't be fully isolated, as by default we are using a static context" (from a Slack thread) — [Kotlinlang Slack thread](https://slack-chats.kotlinlang.org/t/499717/i-m-trying-to-run-my-test-suite-which-uses-koin-multithreade); [Koin Puzzler blog post](https://ruslan.ibragimov.by/2024/08/11/koin-puzzler/)
- A related community pattern for tests: wrap the isolated `koinApplication` in a singleton/object exposing a `koin` property, then have a test base class override `getKoin()` from `KoinTest` to point at that isolated instance rather than the global one — [ProAndroidDev article: Koin's Isolated Context](https://proandroiddev.com/koins-isolated-context-803e8047b358)

### Inferences
- "`koinApplication { }` for KMP/multi-instance" (as framed in the task) is best understood as: any platform or module that needs its own private DI graph not shared with (or visible to) the app's global Koin graph — common in KMP shared SDKs embedded into a host app that has its own, separate `startKoin` call, and in test harnesses that want hermetic, parallel-safe containers.

### Gaps
- None major; this topic was the most thoroughly and consistently documented of all researched areas (single authoritative page plus corroborating community sources with no contradictions).

---

## Koin Annotations (KSP) vs the newer Koin Compiler Plugin

### Takeaway
"Koin Annotations" (the annotation set: `@Single`/`@Singleton`, `@Factory`, `@Module`, `@ComponentScan`, etc.) is not itself deprecated, but its original KSP-based processor (`koin-ksp-compiler`) **is deprecated and slated for removal**, superseded by the newer **Koin Compiler Plugin** — a K2 Kotlin compiler plugin that processes the *same* annotations (plus powers a new "Compiler Plugin DSL" for classic `module { }` blocks) and adds build-time dependency-graph checking that neither the classic DSL nor the old KSP processor provided.

### Cited Findings
- "The KSP processor is deprecated, but Koin Annotations themselves are not. The Compiler Plugin is now the recommended path, and it processes the same annotations." — synthesized from official docs via search, primary pages: [Koin Compiler Plugin intro](https://insert-koin.io/docs/intro/koin-compiler-plugin), [KSP Processor Setup (Deprecated)](https://insert-koin.io/docs/setup/annotations-ksp)
- "The Koin Compiler Plugin is the recommended approach for all new Kotlin 2.x projects." It "is a K2 compiler plugin, not KSP or annotation processing," and "powers both the Compiler Plugin DSL and annotations" — [Koin Compiler Plugin intro](https://insert-koin.io/docs/intro/koin-compiler-plugin)
- "The legacy KSP processor `koin-ksp-compiler` is deprecated and will be removed in a future Koin version." Its setup doc is literally titled "KSP Processor Setup (**Deprecated**)" — [KSP Processor Setup (Deprecated)](https://insert-koin.io/docs/setup/annotations-ksp)
- "The annotations themselves are not deprecated — only the KSP-based processor that used to handle them. The annotations are processed by the Compiler Plugin, so you don't need KSP." — synthesized from official docs via search
- Migration described as "mostly mechanical": update to Kotlin 2.x, drop `koin-ksp-compiler` in favor of the Compiler Plugin, keep existing annotations; one summarized doc instruction: "remove KSP and `koin-ksp-compiler`, apply the Compiler Plugin, delete the generated sources, and replace `modules(MyModule().module)` with `startKoin<MyApp>()`" — synthesized from official docs via search (exact migration-guide page URL not independently re-fetched)
- "Classic DSL is not deprecated" — hand-written `module { single { X(get()) } }` blocks remain fully supported and don't require the Compiler Plugin at all — synthesized from official docs via search; corroborated directly by the fetched Compiler Plugin page: "The Classic DSL does not require the plugin, but the plugin adds these checks if it is applied."
- What problem the Compiler Plugin solves vs. the classic DSL: with classic DSL you write out `single { UserRepository(get(), get()) }` by hand; with the plugin, `single<UserRepository>()` alone is enough, and "during compilation, this becomes the equivalent of `single { UserRepository(get(), get()) }`... the generated code is the same runtime code the Classic DSL uses, so nothing changes at runtime" — plus the plugin "checks your `get()` calls and `singleOf(::T)` definitions at build time" (compile-time dependency-graph validation) — [Koin Compiler Plugin intro](https://insert-koin.io/docs/intro/koin-compiler-plugin)
- What problem it solves vs. the old KSP annotation processor: the Compiler Plugin page states it "replaces the deprecated KSP processor `koin-ksp-compiler`, with the same annotations," and that existing annotation-based code can move over via a migration guide — [Koin Compiler Plugin intro](https://insert-koin.io/docs/intro/koin-compiler-plugin)
- Full current (v4.2) annotation inventory, confirming `@Single`/`@Singleton` both exist and are documented as equivalent (with `@Singleton` recommended for JSR-330 alignment): `@Singleton`/`@Single` (singleton), `@Factory` (new instance per request), `@Scoped` + `@Scope(Type::class)` or `@Scope(name = "...")` (scoped-per-scope-instance), `@KoinViewModel` (ViewModel, needs `koin-core-viewmodel`), `@KoinWorker` (Android WorkManager worker, needs `koin-android-workmanager`), `@ViewModelScope`/`@ActivityScope`/`@ActivityRetainedScope`/`@FragmentScope` (scope archetypes), `@ScopeId` (resolve from a named scope other than current), `@Named`/`@Qualifier` (qualifiers, also accepts `jakarta.inject`/`javax.inject` equivalents), `@InjectedParam` (runtime parameter, checked at compile time), `@Property`/`@PropertyValue` (config-value injection, with a `KOIN-P001` build warning if no default is given), `@Provided` (marks a type as available at runtime even if its declaration isn't visible to the compiler), `@Module` (groups definitions, takes `includes`/`createdAtStart`), `@ComponentScan("pkg1","pkg2")` (scans given packages — and their subpackages — for annotated classes/functions), `@Configuration` (labels a module for `@KoinApplication` loading; default label `"default"`), `@KoinApplication(modules = [...], configurations = [...])` (marks the app entry point consumed by `startKoin<MyApp>()`), `@Monitor` (Kotzilla SDK tracing, needs `io.kotzilla:kotzilla-core`, else `KOIN-M001` build warning) — [Annotations inventory](https://insert-koin.io/docs/reference/koin-annotations/annotations-inventory)
- Removed/obsolete annotations from the KSP era: `@ExternalDefinition`, `@MetaDefinition`, `@MetaModule`, `@MetaApplication` — described as "KSP-internal" and "aren't used by the Compiler Plugin" — [Annotations inventory](https://insert-koin.io/docs/reference/koin-annotations/annotations-inventory)
- `@ComponentScan` scanning is Gradle-source-local: "when using `@ComponentScan`, KSP will only scan the current Gradle source, not across multiple modules" (this specific constraint was documented under the old KSP processor context, from a third-party tutorial) — [Hyperskill tutorial](https://hyperskill.org/learn/step/29635)
- Dependencies for the old KSP-based setup (for reference / legacy projects): `io.insert-koin:koin-core`, `io.insert-koin:koin-annotations`, and the KSP compiler `io.insert-koin:koin-ksp-compiler` — [Hyperskill tutorial](https://hyperskill.org/learn/step/29635)
- Koin 4.2.0 (current-generation release referenced across sources) is built on Kotlin 2.3.20 and adds a Ktor 3.4 DI Bridge, a new Dagger Bridge (`koin-dagger-bridge`), a `strictOverride` option, and `koinActivityInject`; 4.2.1 patched a binary-compatibility regression in `runOnKoinStarted` on JVM and added "Compile Safety with Koin Compiler 1.0.0-RC1" — [Koin 4.2.0 release notes](https://newreleases.io/project/github/InsertKoinIO/koin/release/4.2.0); [SourceForge mirror, 4.2.0 release](https://sourceforge.net/projects/koin.mirror/files/4.2.0/)

### Inferences
- The overall arc of "what problem Koin Annotations/Compiler Plugin solve" vs. hand-written DSL: hand-written `module { }` blocks require manually keeping every `get()` call in sync with constructor signatures (a refactor hazard with no compile-time safety net); Koin Annotations removed the *module-authoring* boilerplate by letting the class declaration itself (`@Single`, `@Factory`, etc.) imply the module entry, discovered via `@ComponentScan`; the Compiler Plugin is a second-generation improvement that keeps that same annotation surface but replaces the KSP code-generation backend with a K2 compiler-plugin backend that both generates the same runtime wiring *and* performs compile-time graph validation (catching missing/ambiguous dependencies at build time) — something neither the classic DSL nor the original KSP processor did.
- Because the Compiler Plugin "powers both the Compiler Plugin DSL and annotations," a project can now mix three styles in principle (classic hand-written DSL, Compiler Plugin DSL like `single<UserRepository>()`, and full annotation-driven `@Singleton class UserRepositoryImpl`) — all compiling down to the same runtime single/factory/scoped machinery.

### Gaps
- Did not successfully fetch the full prose text of the official "What is Koin Annotations?" (`https://insert-koin.io/docs/intro/what-is-koin-annotations`) page — two attempts returned empty content — so the framing of "what problem Koin Annotations solve" above is reconstructed from the Compiler Plugin intro page, the Annotations inventory page, and secondary/community sources (ITNEXT, Hyperskill, klibs.io) rather than quoted directly from that specific intro page.
- Exact version number at which the KSP processor was first marked deprecated, and the precise Koin/Annotations version pairing (the cheat sheet reference found Koin 3.5 + Annotations 1.2, while one blog used Annotations 2.3.2-Beta1) is inconsistent across sources and was not resolved to one authoritative figure — flagging this version inconsistency explicitly rather than picking one arbitrarily.
