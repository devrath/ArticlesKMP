# Koin vs Dagger vs Hilt — Technical Comparison for Interview Prep

## Compile-time (Dagger/Hilt) vs runtime (Koin) dependency resolution — what actually happens at build time / request time

### Takeaway
Dagger/Hilt's annotation processor (kapt or KSP) statically analyzes `@Component`/`@Module`/`@Inject`/`@Provides` annotations and generates plain Kotlin/Java source (a `Dagger<Component>Impl` class plus a `Factory`/`Provider`-implementing class per binding) during compilation, so the dependency graph is fixed and validated before the app ever runs. Koin has no code generation in its default DSL form: `module { single { ... } }` blocks are ordinary Kotlin lambdas stored in a runtime registry (a map-like `BeanDefinition` store), and a dependency is only looked up — via reified generics matching on `KClass`/type — when `get()` or constructor injection actually executes.

### Cited Findings
- Dagger's official site describes it as "a fully static, compile-time dependency injection framework for Java, Kotlin, and Android," built to address "development and performance problems that have affected reflection-based solutions." — [Dagger](https://dagger.dev/)
- The official Dagger README states Dagger does its analysis at compile time, with no reflection or runtime bytecode generation, and generates plain source code. — [Dagger GitHub](https://github.com/google/Dagger)
- Dagger generates a `Factory` class (implementing the JSR-330 `Provider` interface, whose `get()` returns the instance) for each `@Inject`-annotated constructor and each `@Provides` method; a generated `Dagger<Component>Impl` wires these factories together. — [ProAndroidDev: Deep dive into Dagger generated code](https://proandroiddev.com/58f3cb9563de)
- A worked Kotlin example: a `@Component.Factory` nested interface with a `create()` method is generated, and callers invoke `DaggerAppComponent.factory().create(...)` to build the graph — showing the generated component is a concrete, directly-callable class rather than something resolved via reflection. — [msfjarvis.dev: Dagger the easy way](https://drafts.msfjarvis.dev/posts/dagger-the-easy-way--part-1) / [gurubase.io explainer](https://gurubase.io/g/android/purpose-of-component-factory-in-dagger)
- Koin's own documentation (comparison page) states that Hilt/Dagger "generate the dependency graph as Java source at build time via KSP or kapt" and the app "calls generated factories directly, with no lookup at runtime," while Koin "keeps the graph in a runtime container, registered at startup and resolved by type on request." — [insert-koin.io: Koin vs Hilt](https://insert-koin.io/docs/intro/koin-vs-hilt)
- A CommonsWare community thread summarizes the mechanical difference plainly: "Dagger code-generates all of the plumbing to connect injection sites with their sources of injected objects. With Koin, that is handled at runtime." — [CommonsWare community: Replacing Koin with Dagger?](https://commonsware.com/community/t/replacing-koin-with-dagger/423)
- One comparison notes Koin's DSL registers definitions without annotations at all — "To register a dependency in Koin we don't use any annotations since Koin does not generate any code" (true for the classic DSL; Koin now also ships an optional Annotations/Compiler-Plugin mode — see below). — [Medium: Dagger vs Koin](https://medium.com/@lukmagayoarizky/battle-between-dagger-and-koin-in-android-development-9988893ca6c)
- Koin now also offers a **Koin Compiler Plugin** (successor to `koin-ksp-compiler`, which is deprecated) that adds build-time graph validation on top of the runtime DSL/annotations model, narrowing but not eliminating the compile-time/runtime gap. — [Koin blog: Compiler Plugin 1.0.0-RC1](https://blog.insert-koin.io/unlocking-koin-compile-safety-6278840ab171), [insert-koin.io: Compile-Time Safety](https://insert-koin.io/docs/reference/koin-compiler/compile-safety)

### Inferences
- The core architectural split is: Dagger/Hilt = static graph, resolved to direct function calls before the app runs; Koin (classic DSL) = dynamic graph, resolved to a map/type lookup while the app runs. This is the single fact from which almost every other difference (performance, error timing, KMP support, build time) follows.
- Koin's newer Compiler Plugin is an attempt to get Dagger-like build-time checking without abandoning the runtime-resolution execution model — worth mentioning in an interview as a sign the two approaches are converging somewhat, but the underlying execution (generated direct calls vs. runtime container lookup) still differs.

### Gaps
- I did not find a source showing a byte-for-byte example of generated Dagger source next to the Koin DSL equivalent for the exact same binding; the ProAndroidDev "under the hood" article that likely had this (https://proandroiddev.com/how-dagger-hilt-and-koin-differ-under-the-hood-c3be1a2959d7) returned HTTP 403 and could not be fetched.

---

## Performance implications: Dagger/Hilt's near-zero runtime DI overhead vs Koin's small-but-nonzero lookup overhead

### Takeaway
Because Dagger/Hilt's generated code is just direct constructor/method calls (no reflection, no map lookup), its *runtime* DI overhead is effectively zero beyond ordinary object construction. Koin performs a runtime lookup (by reified type, through its registry) on every `get()`/injection, which is real but, per most community benchmarking and Koin's own docs, is small enough to be negligible for typical app sizes — though sources disagree on magnitude, and one recent formal-ish benchmark found no meaningful difference at all.

### Cited Findings
- Koin's own comparison docs state plainly that Hilt has "no runtime lookup," while Koin "costs a lookup per resolution plus registration work at startup" — an admission from the Koin side itself that there is real (if small) runtime cost. — [insert-koin.io: Koin vs Hilt](https://insert-koin.io/docs/intro/koin-vs-hilt)
- A 2024 benchmark article ("Benchmarking Koin vs. Dagger Hilt in Modern Android Development," tested against Google's "Now in Android" sample app on Koin 4.0.1-Beta1 vs. Dagger Hilt 2.52) reportedly found Koin matching Hilt in performance. I could not fetch the article directly (403 error) to extract exact figures/methodology, so this claim is second-hand via search-result summary only. — [ProAndroidDev (via search snippet)](https://proandroiddev.com/benchmarking-koin-vs-dagger-hilt-in-modern-android-development-2024-ff7bb40470df)
- Conversely, a DEV Community comparison claims Koin is "slightly slower" than Dagger 2 and Hilt, "especially in large-scale applications," and that "injecting dependencies is also a bit faster in Dagger than in Koin." — [DEV Community: Koin vs Dagger 2 vs Hilt](https://dev.to/mikkelofficial7/koin-vs-dagger-2-vs-hilt-which-one-should-we-use-1e0n)
- A CommonsWare community thread states the general-case reasoning: "Dagger generates all of the plumbing... With Koin, that is handled at runtime, and it will be slower as a result" — a qualitative claim, not a benchmark. — [CommonsWare community thread](https://commonsware.com/community/t/replacing-koin-with-dagger/423)
- One article adds that Koin "takes a significant time to set up" (startup/registration cost), distinct from per-injection lookup cost. — [search-result summary citing comparison article]

### Inferences
- The performance claims genuinely conflict across sources: at least one 2024 benchmark says "no practical difference," while other community write-ups say Koin is measurably (if not dramatically) slower, particularly at scale or during startup (registering all modules). This is a case where community opinion is split rather than unanimous — flag this explicitly in the report rather than picking a side.
- The theoretically sound takeaway (consistent across all sources) is directional, not magnitude-specific: Dagger/Hilt removes a layer of indirection (lookup) that Koin keeps; whether that lookup cost is "negligible" depends on injection graph size, how many `get()` calls happen on hot paths (e.g., inside RecyclerView binding) vs. once at object-graph construction time, and device performance.

### Gaps
- No source gave hard numbers (e.g., milliseconds, nanoseconds per resolution, or percentage startup time difference) that I could verify first-hand — the 2024 benchmark article that most likely contains these numbers was inaccessible (403 Forbidden on WebFetch). The report should note that specific benchmark figures could not be independently verified and should be cited cautiously, attributed as "a 2024 community benchmark on the Now in Android sample app reported near-parity" rather than as a hard verified statistic.
- No official Google/Dagger-team or Koin-maintainer (Arnaud Giuliani) statement with specific quantified overhead numbers was found in this research pass.

---

## Build time / build complexity

### Takeaway
Dagger/Hilt's annotation processing (kapt or KSP) adds measurable build time and an extra compilation step; KSP is generally faster than the older kapt but results are project-dependent and not universally faster. Koin's classic DSL requires no annotation processor at all, so there is no equivalent build-time tax — builds are typically faster and compiler errors are plain Kotlin errors rather than annotation-processor-generated errors.

### Cited Findings
- kapt's stub-generation step is a major cost driver: one project-level analysis found 75.8% (24.2 seconds) of relevant build time was spent generating kapt stubs, which KSP is designed to avoid. — [search-result summary citing ObjectBox analysis via DEV Community thread]
- A controlled "Now in Android" test with Dagger/Hilt 2.48 found KSP decreased annotation-processing build time versus kapt, though configuration time still made up roughly 30% of total build time in that run. — [search-result summary, original source likely proandroiddev.com migration articles]
- Counter-evidence exists: a GitHub issue on the Dagger repo is titled "[KSP] Performance regression vs KAPT" (google/dagger #4180), and a Kotlin Slack thread reports KSP-based builds taking "almost twice as long" in at least one project, with Dagger suspected as the cause (poster uncertain, KSP version unspecified). — [GitHub: google/dagger#4180](https://github.com/google/dagger/issues), [Kotlin Slack thread (via search summary)]
- Mixing kapt and KSP for the same library (e.g., during a partial migration) runs both processors and can be slower than kapt alone — a practical migration pitfall. — [search-result summary]
- Vendor/community blog estimates of KSP's speedup over kapt range from "25–50% typical" to "50–70%," but these are not from one consistent, controlled benchmark and should be treated as rough, directionally-consistent estimates rather than hard facts. — [search-result summary citing multiple ProAndroidDev/Medium articles]
- The Kotzilla (Koin-affiliated) blog states plainly that Dagger2/Hilt is "complicated to configure" and that "long compilation times don't help," contrasting with Koin's lack of an annotation-processing step — though this source is explicitly promotional for Koin and gives no quantified comparison. — [Kotzilla blog: Why migrate from Dagger2/Hilt to Koin](https://blog.kotzilla.io/why-migrate-from-dagger2/hilt-to-koin)

### Inferences
- The directionally consistent, low-controversy claim for an interview answer: "Dagger/Hilt adds an annotation-processing pass (kapt/KSP) to every build, which is close to zero for Koin's classic DSL, so Koin builds are usually faster — but the gap has narrowed since Dagger/Hilt moved to KSP, and build time differences are project-size-dependent, not fixed constants."
- Error message quality is a related, often-cited build-complexity point: annotation-processor errors (Dagger/Hilt) can be cryptic, pointing at generated code rather than the user's own source, whereas Koin's classic DSL errors are either plain Kotlin compiler errors (e.g., type mismatch in a lambda) or Koin-specific runtime exceptions (e.g., `NoBeanDefFoundException` / `NoDefinitionFoundException`) — this specific comparative claim about Dagger's error cryptic-ness vs Koin's plainer errors is consistent with general DI-framework literature but I did not find a source explicitly contrasting the two error styles side-by-side in this research pass.

### Gaps
- No single, apples-to-apples benchmark (same app, same machine, same date) comparing Koin build time vs Dagger/Hilt (KSP) build time was found — all figures found are either kapt-vs-KSP (both Dagger-side) or qualitative/promotional claims about Koin. This should be flagged in the report as an area where hard comparative data is thin.

---

## Error detection timing: compile-time (Dagger/Hilt) vs runtime (Koin), and Koin's opt-in verification tools

### Takeaway
Dagger/Hilt always catches missing, ambiguous, or cyclic bindings at compile time — the build simply fails. Koin's classic DSL defers this to runtime (the `NoDefinitionFoundException`/`NoBeanDefFoundException`-style crash on `get()`), unless the team proactively uses `checkModules()`/`verify()` in a unit test, or adopts Koin's newer Compiler Plugin, which performs real build-time graph validation and (per Koin's own announcement) is intended to replace the older `checkModules()`/`verify()` test APIs.

### Cited Findings
- Koin's own docs/comparison page: "Missing bindings are caught at build time only with the Compiler Plugin. Otherwise they surface at runtime or in a verification test," versus Hilt where "Missing bindings are always caught at build time." — [insert-koin.io: Koin vs Hilt](https://insert-koin.io/docs/intro/koin-vs-hilt)
- `checkModules()` is described as a test-suite tool: it instantiates every registered definition and verifies dependencies resolve, but problems surface only when the test suite is run, not during a normal build. — [helpmetest.com: Koin Testing Guide](https://helpmetest.com/blog/koin-testing-guide/)
- The Koin Compiler Plugin checks the dependency graph during compilation; a missing definition, wrong qualifier, or circular dependency fails the build with a file/line reference, similar in spirit to Dagger's compile-time errors. Compile safety is enabled by default (`compileSafety = true`). — [insert-koin.io: Compile-Time Safety](https://insert-koin.io/docs/reference/koin-compiler/compile-safety)
- The Koin Compiler Plugin 1.0.0-RC1 (requires Koin 4.2.1 + Kotlin 2.3.20 per the announcement) is positioned by Koin's own blog as a replacement for `checkModules()`/`verify()`. — [Koin blog: Compiler Plugin 1.0.0-RC1](https://blog.insert-koin.io/unlocking-koin-compile-safety-6278840ab171)
- A caveat/limitation: the plugin skips validation when a compilation has no Koin entry point, and a 1.1.0 release note says per-module validation was removed — so only compilations that assemble a real, complete graph get checked, not arbitrary partial modules. — [insert-koin.io docs](https://insert-koin.io/docs/reference/koin-compiler/compile-safety), [GitHub: koin-compiler-plugin releases](https://github.com/InsertKoinIO/koin-compiler-plugin/releases)
- A Kotlin Slack thread pushes back somewhat on the "replacement" framing: it notes the KSP-based check in Koin Annotations 1.3 covers only *annotation-based* definitions, implying `verify()`/`checkModules()` remain necessary for projects using the plain DSL rather than annotations. Sources conflict here and should be presented as such. — [Kotlin Slack chat (via search summary)](https://slack-chats.kotlinlang.org/t/15703523/)
- A commonly repeated framing-level point (useful for interview "gotcha" questions): one source calls out that technically, "only Dagger uses a dependency injection (DI) pattern; Koin and Kodein are service locators" — i.e., Koin's `get()` call-site lookup resembles the Service Locator pattern more than classic constructor-based DI, which is a frequently debated classification point in the Android community. — [Medium comparison article, via search summary]

### Inferences
- For interview purposes, the safest, most defensible answer is: "Dagger/Hilt: always compile-time. Koin classic DSL: runtime by default, but can be pushed to test-time via `checkModules()`/`verify()`, or to build-time via the newer Koin Compiler Plugin — though that plugin's coverage of plain-DSL (non-annotation) graphs has caveats/limitations per module-level validation changes."
- The "Koin is a service locator, not true DI" critique is a classic, legitimate interview talking point distinguishing Koin's `get()`-at-call-site resolution model from Dagger's constructor-injection-only model, and is worth including as a nuanced/contrarian point rather than taking Koin's self-description as "DI" at face value.

### Gaps
- I did not find a definitive, up-to-date (2025/2026) statement reconciling whether the Koin Compiler Plugin now fully covers plain DSL modules (not just Koin Annotations) — the sources found are a mix of different plugin versions (`koin-ksp-compiler` deprecated → Koin Compiler Plugin) and dates, so the report should present this as an evolving area and recommend checking current Koin docs for the latest coverage.

---

## Boilerplate & learning curve: Dagger vs Hilt vs Koin for a sample dependency graph

### Takeaway
Hilt meaningfully reduces Dagger's raw boilerplate for Android apps by providing predefined components tied to the Android lifecycle and generated base classes, removing the need to hand-write `@Component` interfaces and manually wire them into `Application`/`Activity`. Koin's DSL is the most concise of the three for simple cases, requiring no annotations, no modules-as-interfaces, and no `@Binds`/`@Provides` ceremony for interface bindings — at the cost of losing compile-time safety by default.

### Cited Findings
- Per Koin's own comparison docs, boilerplate differences for common cases:
  - **Class with dependencies:** Hilt needs `@Inject constructor(...)` on the class plus a separate abstract `@Binds` module method to bind an interface to its implementation. Koin needs only a `@Singleton`-style annotation (or a `single { ... }` DSL entry) on/for the class, with no separate binding module. — [insert-koin.io: Koin vs Hilt](https://insert-koin.io/docs/intro/koin-vs-hilt)
  - **Third-party / non-constructor-injectable classes:** Both frameworks use provider functions, but Hilt requires `@InstallIn(...)` plus `@Provides` on a Dagger module method; Koin requires neither — just a `single { ThirdPartyClass(...) }` lambda. — same source.
  - **ViewModel:** Hilt requires `@HiltViewModel` on the ViewModel class, `@Inject` on its constructor, and `@AndroidEntryPoint` on the hosting Activity/Fragment. Koin requires `@KoinViewModel` (if using annotations) or simply `viewModel { ... }` in the module DSL, consumed via `by viewModel()` / `koinViewModel()`. — same source.
  - **Multi-module setup:** Hilt uses `@InstallIn(SingletonComponent::class)` (or other component scopes) across modules plus `@HiltAndroidApp` on the Application class. Koin uses `@Configuration`/`@ComponentScan` (with annotations) or just composing multiple `module { }` blocks, started via `startKoin { }` / `@KoinApplication`. — same source.
- Community sentiment is broadly consistent that Hilt is more effort than Koin for the same outcome: "Hilt will require more effort when compared to simpler DI frameworks like Koin," balanced against "Hilt is designed specifically for Android development, providing seamless integration with Android components." — [search-result summary of multiple comparison blogs]
- Migration-pattern evidence (from a Koin-affiliated source, so read with that bias in mind) shows the annotation-level 1:1 swaps: `@Singleton` → `single`, `@HiltViewModel` → `viewModel`, `@Provides`/`@Binds` → `factory` — illustrating Koin's DSL as a flatter, less-indirected equivalent of the same concepts. — [Kotzilla blog](https://blog.kotzilla.io/why-migrate-from-dagger2/hilt-to-koin)

### Inferences
- The learning-curve ordering most sources implicitly agree on: raw Dagger (steepest — manual `@Component`, `@Module`, `@Subcomponent`, scope annotations) > Hilt (flatter — Google predefines the component hierarchy and generates base classes, removing most manual wiring) > Koin (flattest — plain Kotlin DSL, no annotation vocabulary required to get started, though Koin Annotations reintroduces some annotation-style syntax as an opt-in).
- Hilt's core boilerplate reduction over raw Dagger specifically comes from NOT requiring developers to declare `@Component`/`@Subcomponent` interfaces by hand — Hilt generates these for standard Android classes automatically once `@AndroidEntryPoint`/`@HiltAndroidApp` are applied.

### Gaps
- I did not find a side-by-side line-of-code count (e.g., "building X graph takes N lines in Dagger vs N lines in Hilt vs N lines in Koin") from a rigorous, recent source — available comparisons are qualitative/example-based rather than quantified.

---

## Hilt's Android lifecycle integration specifics vs Koin's koin-android equivalents

### Takeaway
Hilt ties dependency scopes directly to Android's own component lifecycle via a fixed hierarchy of generated components (`SingletonComponent`, `ActivityComponent`, etc.), activated through `@HiltAndroidApp`/`@AndroidEntryPoint`/`@HiltViewModel`, plus official first-party support for WorkManager via `HiltWorkerFactory`. Koin achieves comparable lifecycle awareness through `koin-android` helpers (`androidContext()`, `viewModel { }` DSL, `ScopeActivity`/`ScopeFragment`, manual `createScope()`/`closeScope()`) and a separate `koin-androidx-workmanager` module — all manually wired rather than generated, with some reported rough edges.

### Cited Findings
- **`@HiltAndroidApp`**: Placed on the Application class; required for every Hilt app; triggers code generation including an application-level base class acting as the top-level dependency container. — [developer.android.com, via search summary]
- **`@AndroidEntryPoint`**: Enables field injection in Android framework classes (Activities, Fragments, Views, Services, BroadcastReceivers); Hilt generates an individual Hilt component for each annotated class. — same source.
- **`@HiltViewModel`**: Marks a ViewModel for Hilt to provide instances of, integrating with Android's `ViewModel` architecture component. — same source.
- **`@InstallIn`**: Required on every Hilt module to declare which generated Android component (e.g., `SingletonComponent`, `ActivityComponent`) it is installed into — unlike plain Dagger modules, which don't need this because you build the component graph by hand. — [developer.android.com, via search summary]
- **`SingletonComponent`**: Used to install a module so bindings are available application-wide. **`ActivityComponent`**: scopes a binding to a single Activity instance. — same source.
- **WorkManager (Hilt)**: Official integration via the `androidx.hilt:hilt-work` artifact plus an annotation processor (current docs show `ksp("androidx.hilt:hilt-compiler:1.4.0")`, Kotlin; older docs used kapt 1.0.0). Workers are annotated `@HiltWorker` with `@AssistedInject` constructors (Context/WorkerParameters marked `@Assisted`); only `@Singleton` or unscoped bindings are usable inside Workers. The Application implements `Configuration.Provider`, injects a `HiltWorkerFactory`, and passes it via `setWorkerFactory` in the WorkManager configuration. — [developer.android.com: Use Hilt with other Jetpack libraries](https://developer.android.com/training/dependency-injection/hilt-jetpack)
- **Koin's `koin-android` equivalents**:
  - `androidContext()` is passed inside `startKoin { }` to make the Android `Context` available to the graph. — [general Koin docs knowledge, consistent across search results]
  - ViewModels use the `viewModel { }` DSL declaration and are retrieved with `by viewModel()` / `koinViewModel()`; Koin 4.x also offers a `ScopeViewModel` base class and a `viewModelScopeFactory` option to auto-create a ViewModel-tied scope. — [insert-koin.io docs: Android Scopes](https://insert-koin.io/docs/reference/koin-android/scope)
  - A ViewModel-scoped Koin `Scope` survives until the ViewModel's `onCleared()` — the recommended pattern is to close the scope manually inside `onCleared()`. Koin also provides `ScopeActivity`/`ScopeFragment` base classes and an `AndroidScopeComponent` interface with an `onCloseScope()` hook for pre-destruction cleanup. — same source.
  - Custom shared scopes can be created manually via `getKoin().createScope("session", named("session"))`, with the docs explicitly cautioning that manually created scopes must be manually closed. — same source.
  - A known GitHub issue (reported on Koin 3.5.0) describes a bug where closing a scope and creating a new one can still resolve a ViewModel instance from the old (closed) scope rather than a fresh one — a real rough edge in the scope-lifecycle mechanism. — [GitHub: InsertKoinIO/koin#1654](https://github.com/InsertKoinIO/koin/issues/1654)
  - **WorkManager (Koin)**: `koin-androidx-workmanager` module provides a `workManagerFactory()` call (used inside `startKoin { }`) and a `worker { }` / `workerOf(::MyWorker)` DSL for declaring Worker classes with dependencies resolved via `get()`. Setup requires correct ordering (Koin must start before WorkManager tries to create workers) and often requires removing WorkManager's default manifest-based initializer. `WorkManager` itself is not auto-provided and commonly needs a manual `single { WorkManager.getInstance(get()) }` binding. A GitHub issue (#1729) reports crashes when `koin-androidx-workmanager` is combined with AndroidX Startup-based Koin initialization; a workaround some developers use is dropping the module entirely and making Worker classes `KoinComponent`s that pull dependencies directly when they run. — [GitHub: InsertKoinIO/koin#1729, #1623](https://github.com/InsertKoinIO/koin/issues/1729), Slack/Stack Overflow threads (via search summary)

### Inferences
- Hilt's lifecycle integration is more "batteries-included" and less error-prone in practice because the component hierarchy, scoping, and generated base classes are fixed and validated at compile time — there is no equivalent to Koin's scope-leak bug class (stale instance from a closed scope) because Hilt's generated components don't allow that kind of manual-lifecycle mismatch.
- Koin's WorkManager story is comparatively less polished/first-party-robust than Hilt's — multiple real GitHub issues point to ordering/initialization fragility, whereas Hilt's `HiltWorkerFactory` pattern is a single official, well-documented code path.

### Gaps
- I did not find Koin's own official documentation page for `koin-androidx-workmanager` (only GitHub issues, Slack threads, and gists) — one search result explicitly noted that even Koin's own docs for this module were described by a community member as "confusing." This should be flagged as a documentation gap for Koin specifically, not just a research gap.

---

## Multiplatform support: why Dagger/Hilt are Android/JVM-only and Koin spans all KMP targets

### Takeaway
Dagger (and by extension Hilt, which is explicitly Android-only) depends on Java/Kotlin annotation processing (kapt/KSP), which is a JVM-toolchain concept with no equivalent code-generation pathway for Kotlin/Native (iOS, and other non-JVM KMP targets) — so Dagger-generated code simply does not exist on those targets. Koin's DSL is pure Kotlin (lambdas, reified generics, a runtime container) with no dependency on JVM-specific tooling, so the identical `module { }` code compiles and runs unmodified on every Kotlin Multiplatform target (JVM, Android, iOS/Kotlin-Native, Desktop, JS/Wasm, Ktor).

### Cited Findings
- "When the Kotlin compiler targets iOS (Kotlin/Native), there's no annotation processor running. The generated Dagger code simply doesn't exist on the iOS side." — [via search summary, source attributed to a KMP-focused blog comparing Koin/Dagger]
- A CommonsWare community thread makes the identical architectural point: Dagger "ties you to the JVM," whereas Koin is "Kotlin/Multiplatform-friendly." — [CommonsWare community: Replacing Koin with Dagger?](https://commonsware.com/community/t/replacing-koin-with-dagger/423)
- Koin is described (via an indexed mirror of its own GitHub repo) as "a pragmatic and lightweight dependency injection framework for Kotlin and Kotlin Multiplatform (KMP)," explicitly supporting JVM, Android, Compose, iOS, Desktop, Web (JS/Wasm), and Ktor. — [Algolia-indexed Koin repo docs](https://docsearch.algolia.com/mcp/docs/repo/insertkoinio/koin)
- The Kotzilla (Koin-affiliated) migration article states Dagger2/Hilt "aren't compatible" with Kotlin Multiplatform, citing this as a primary motivation for teams sharing Android/iOS code to migrate to Koin. — [Kotzilla blog](https://blog.kotzilla.io/why-migrate-from-dagger2/hilt-to-koin)
- Koin's `koin-ksp-compiler` (its own earlier KSP-based annotation processor, used for Koin Annotations, not for cross-platform codegen of the DSL itself) is noted as deprecated in favor of the newer Koin Compiler Plugin — worth noting that Koin itself has *some* optional KSP/compiler-plugin tooling, but this is for compile-time validation/annotation support, not a replacement for its core pure-Kotlin runtime resolution model, and does not reintroduce the JVM-only limitation into Koin's core DSL path. — [insert-koin.io docs, via search summary]
- Other KMP-capable DI alternatives mentioned alongside Koin in this space: **kotlin-inject** (compile-time, supports all KMP targets via Kotlin Symbol Processing rather than Java annotation processing) and **Metro** (generates code in the compiler's IR phase). These are offered as evidence that "compile-time DI for KMP" is possible in principle (via KSP/IR rather than kapt), but Dagger/Hilt specifically have not adopted such a cross-platform codegen path. — [abd3lraouf-dev blog: Why Koin Won the KMP DI Battle](https://abd3lraouf-dev.pages.dev/blog/kmp-koin-introduction) (opinion blog; treat comparison table with caution per the source itself)
- A common pragmatic hybrid pattern reported in the Kotlin community: using Koin only in KMP shared/common code, while continuing to use Dagger/Hilt in the Android-only application layer. — [Kotlin Slack thread, via search summary]

### Inferences
- The root technical cause is specifically about *tooling*, not language: annotation processing (kapt) and even KSP as traditionally used by Dagger are JVM/Android-build-pipeline concepts tied to javac/Kotlin-JVM compilation; Kotlin/Native (iOS) has a different compiler backend (the Kotlin/Native backend, not JVM bytecode) with no equivalent processor hook in Dagger's current implementation. Koin avoids this entirely by not generating code for its core DSL — it is just Kotlin standard-library-level code (lambdas, generics, collections) that any Kotlin compiler backend already knows how to compile.
- Hilt being explicitly "Android-only" (layered on top of Dagger, itself JVM-only) means Hilt is a strict subset limitation: even a JVM-only non-Android Kotlin project couldn't use Hilt (only Dagger), and neither could use either framework for a shared KMP module targeting iOS.

### Gaps
- I did not find an official Google/Dagger statement explicitly confirming Dagger has no plans to support Kotlin/Native — the "architecturally impossible without rework" framing above is based on community/technical-blog explanations of kapt's JVM dependency, not a primary Google source. This should be presented as the consistent community/technical understanding rather than an official Google confirmation.

---

## "When to choose which" — community guidance and pros/cons lists

### Takeaway
The consistent, cross-source community guidance is: choose Dagger/Hilt for large, long-lived, performance-sensitive, Android-only (or JVM-only) apps where compile-time safety and Google's official backing matter; choose Koin for teams prioritizing simplicity, faster onboarding, and Kotlin Multiplatform code-sharing, especially smaller-to-mid-size apps or apps that need to share DI logic with iOS/other KMP targets.

### Cited Findings
- "The sources broadly agree that Hilt or raw Dagger suits large, performance-sensitive projects with compile-time guarantees, while Koin suits teams that want quicker setup and less boilerplate." — [synthesized across proandroiddev.com, medium.com, dev.to, and coderlegion.com comparison articles, per search summary]
- Hilt's selling point is being "designed specifically for Android development, providing seamless integration with Android components like activities, fragments, and view models," and being the Google-backed, officially recommended option for Android apps. — [search-summary across comparison blogs]
- Koin's selling points, repeated across multiple independent sources: lightweight, Kotlin-native (no non-Kotlin framework dependency), no annotation processor required for its classic DSL, native Kotlin Multiplatform support, and generally faster/simpler setup. — [Kotzilla blog](https://blog.kotzilla.io/why-migrate-from-dagger2/hilt-to-koin), [Medium: Koin vs Hilt pros and cons](https://medium.com/@xrkaitatzis/koin-vs-hilt-pros-and-cons-6db677f32b21)
- A frequently repeated caution/con for Koin: because it's a runtime/service-locator-style mechanism, teams give up compile-time guarantees by default and must opt in to `checkModules()`/`verify()` or the newer Compiler Plugin to regain comparable safety. — [insert-koin.io: Koin vs Hilt](https://insert-koin.io/docs/intro/koin-vs-hilt)
- A frequently repeated caution/con for Dagger/Hilt: steeper learning curve, more boilerplate/ceremony (even with Hilt's reductions), longer build times from annotation processing, and sometimes cryptic generated-code-related compiler errors. — [search-summary across dev.to, coderlegion.com]
- Koin's own official docs do not claim Koin is strictly "better" — they present Hilt as stronger specifically on complete build-time graph verification, zero runtime lookup cost, Java support, and ecosystem/official-Android-guidance backing, while listing Koin's strengths as KMP support, runtime flexibility (dynamic module loading/unloading), and less setup/declaration code. This balanced self-comparison from Koin's own docs is a notably even-handed primary source. — [insert-koin.io: Koin vs Hilt](https://insert-koin.io/docs/intro/koin-vs-hilt)

### Inferences
- The strongest, least-disputed interview-ready heuristic: "Need iOS/KMP sharing or want the fastest ramp-up → Koin. Need Google's official Android-only recommendation, maximum compile-time safety, and are fine with more setup/boilerplate and Java interop → Hilt. Need fine-grained control beyond what Hilt's predefined components offer, or are not exclusively targeting Android → raw Dagger."
- Because Hilt is explicitly Google's recommended DI solution for Android apps (per developer.android.com's own framing, consistent with search results throughout this research), any interview answer should note that "official support/recommendation" is itself one of Hilt's selling points, independent of technical merits.

### Gaps
- None significant — this question had broad, consistent cross-source agreement, unlike the performance-benchmark question above.

---

## Common interview-style questions comparing these three frameworks, with strong/correct answers

### Takeaway
Based on the recurring themes across all sources researched, the most likely interview questions cluster around: (1) compile-time vs runtime resolution and its consequences, (2) whether Koin is "really" DI or a Service Locator, (3) Hilt vs Dagger relationship, (4) KMP/platform support, and (5) when to pick which. Below are synthesized Q&A pairs built directly from the cited findings above (not fabricated — each answer traces to a finding already cited in this document).

### Cited Findings (used to construct the Q&A below)
- See all findings cited in the sections above; this section synthesizes rather than introduces new citations.

### Inferences (synthesized interview Q&A)
- **Q: What is the fundamental architectural difference between Dagger/Hilt and Koin?**
  A: Dagger/Hilt resolve the dependency graph at compile time via annotation processing (kapt/KSP), generating a `Dagger<Component>Impl` and `Factory`/`Provider` classes that the app calls directly at runtime with no lookup cost. Koin resolves the graph at runtime: `module { }` DSL blocks register lambdas in a container, and `get()` performs a reified-generic type lookup against that container when called. — supported by insert-koin.io, Dagger GitHub README, ProAndroidDev generated-code deep dive (cited above).
- **Q: Is Koin really "dependency injection"?**
  A: This is debated. Technically, Koin's call-site `get()` resolution resembles the Service Locator pattern more than classic DI, since consuming code actively asks the container for a dependency rather than purely receiving it via constructor; some sources explicitly argue "only Dagger uses a dependency injection (DI) pattern; Koin and Kodein are service locators." A nuanced answer acknowledges Koin is usually marketed/used as DI (especially via constructor injection patterns like `viewModel { MyViewModel(get()) }`) but has Service-Locator characteristics under the hood. — supported by Medium comparison article (cited above).
- **Q: How does Hilt relate to Dagger?**
  A: Hilt is built on top of Dagger, maintained by Google, and reduces Dagger's Android-specific boilerplate by predefining a standard component hierarchy (`SingletonComponent`, `ActivityComponent`, etc.) tied to Android lifecycle classes, plus generating base classes via `@HiltAndroidApp`/`@AndroidEntryPoint` so developers don't hand-write `@Component`/`@Subcomponent` interfaces. — supported by developer.android.com summary and multiple comparison articles (cited above).
- **Q: When are missing dependencies caught in each framework?**
  A: Dagger/Hilt: always at compile time (the build fails). Koin classic DSL: at runtime when `get()` is called, unless you add `checkModules()`/`verify()` unit tests, or adopt the newer Koin Compiler Plugin for build-time validation (with some coverage caveats for non-annotation DSL use). — supported by insert-koin.io Compile-Time Safety docs and Koin Compiler Plugin announcement (cited above).
- **Q: Why can't Dagger/Hilt be used in shared Kotlin Multiplatform code targeting iOS?**
  A: Dagger relies on JVM-based annotation processing (kapt/KSP as traditionally wired) to generate its component/factory code; Kotlin/Native (the iOS compiler backend) has no equivalent annotation-processor hook, so the generated Dagger code simply doesn't exist for that target. Hilt, being Android-specific on top of Dagger, is even more narrowly scoped. Koin avoids the problem because its DSL is plain Kotlin with no code-generation dependency, so it compiles identically on every KMP target. — supported by CommonsWare thread and KMP comparison blog (cited above).
- **Q: What's the real-world performance difference between Koin and Dagger/Hilt?**
  A: Theoretically, Dagger/Hilt has zero added runtime DI overhead (direct generated calls) while Koin adds a small runtime lookup cost per resolution plus startup registration cost — a claim Koin's own docs acknowledge. In practice, community benchmarks disagree: at least one 2024 benchmark on Google's "Now in Android" app reported near-parity, while other community comparisons claim Koin is measurably slower, especially at scale. The honest interview answer acknowledges the theoretical direction is undisputed but the practical magnitude is contested and likely negligible for most typical apps. — supported by insert-koin.io and conflicting DEV Community/ProAndroidDev sources (cited above).
- **Q: If you were starting a new KMP app that targets Android and iOS, which DI framework would you pick, and why?**
  A: Koin, because Dagger/Hilt cannot generate code for Kotlin/Native (iOS) targets, making them unusable for shared DI logic across platforms; Koin's pure-Kotlin runtime DSL works identically on all KMP targets. For an Android-only app, Hilt would typically be preferred for its compile-time safety and official Google backing, unless team priorities favor Koin's simplicity and faster iteration. — supported by multiple sources cited in the Multiplatform and When-to-choose sections above.

### Gaps
- These Q&A pairs are my own synthesis built from findings already cited elsewhere in this document (not newly sourced), per instruction to avoid fabricating new claims — the report writer should feel free to treat this section as "study questions built from the verified findings above" rather than as independently sourced material.

---

## Overall sourcing caveats for the report writer

- Several sources are explicitly Koin-affiliated/promotional (Kotzilla blog, insert-koin.io docs) — their comparisons were still useful and are flagged inline, but claims of Koin superiority from these sources should be framed as "per Koin's own team" rather than neutral fact.
- Two potentially high-value sources (a 2024 ProAndroidDev Koin-vs-Hilt benchmark article, and a ProAndroidDev "under the hood" generated-code comparison article) returned HTTP 403 Forbidden on direct fetch and could only be partially represented via search-engine snippet summaries — treat any claims attributed to these two articles as lower-confidence/secondhand, and the report writer may want to try fetching them again via a different method or search for mirrors/reposts.
- The performance-benchmark question is the one area with genuine, unresolved source disagreement (some say "no meaningful difference," others say "Koin is measurably slower at scale") — this should be presented in the final report as a disputed point, not resolved one way or the other.
