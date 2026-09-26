# Path C install-time pack

Bundle Unitto data files here (unit tables, etc.) then copy into:

`extensions/extension/src/main/assets/pgp-unitto/`

Runtime:

```java
context.getAssets().open("pgp-unitto/…")
```

Optional extract:

```java
File dir = new File(context.getFilesDir(), "pgp-unitto");
```

Patched at Morphe time — not a Play download.
