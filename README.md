# MacroTracker

Android app for logging daily macronutrient intake via natural language chat.

---

## Actualizar el modelo de base de datos (Room)

La base de datos usa Room con **migraciones explícitas obligatorias desde v7**. Cualquier cambio en una entidad sin su migración correspondiente provoca un crash al abrir la app (nunca pérdida silenciosa de datos).

### Versión actual: 8
Fichero de esquema de referencia: `app/schemas/com.example.test1.data.db.AppDatabase/8.json`

---

### Cómo añadir o modificar un campo

#### Caso simple — añadir una columna con valor por defecto

Room puede escribir el SQL automáticamente con `@AutoMigration`.

1. Edita la entidad en `data/db/entity/`.
2. Sube la versión en `AppDatabase.kt`:
   ```kotlin
   @Database(
       version = 8,           // era 7
       autoMigrations = [AutoMigration(from = 7, to = 8)]
   )
   ```
3. Haz build (`.\gradlew.bat assembleDebug`). KSP genera `schemas/…/8.json`.
4. Commitea el `8.json` junto con los cambios de código.

#### Caso complejo — renombrar tabla, mover datos, eliminar columna

Room no puede inferir la intención (renombrar vs. borrar+crear), así que hay que escribir el SQL a mano.

1. Edita la entidad.
2. Sube la versión y añade un objeto `Migration` en `AppDatabase.kt`:
   ```kotlin
   private val MIGRATION_7_8 = object : Migration(7, 8) {
       override fun migrate(database: SupportSQLiteDatabase) {
           // Ejemplo: añadir columna nullable
           database.execSQL("ALTER TABLE food_entries ADD COLUMN notes TEXT")
           // Ejemplo: renombrar columna (SQLite no tiene ALTER COLUMN;
           //   hay que crear tabla nueva, copiar datos, borrar vieja)
       }
   }
   ```
3. Registra la migración en `getInstance()`:
   ```kotlin
   .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
   ```
4. Haz build → se genera `8.json`. Commitéalo.

#### Renombrar columna o tabla con anotaciones (Room 2.4+)

Para casos simples de renombrado Room tiene anotaciones que evitan escribir SQL:

```kotlin
// En la entidad: renombrar columna
@ColumnInfo(name = "nuevo_nombre")
val nuevoNombre: String

// En la migración automática: indicar el renombrado
@AutoMigration(
    from = 7, to = 8,
    spec = AppDatabase.Migration7to8::class
)

// Clase spec dentro de AppDatabase:
@RenameColumn(tableName = "food_items", fromColumnName = "viejo", toColumnName = "nuevo")
class Migration7to8 : AutoMigrationSpec
```

---

### Reglas que no hay que olvidar

| Regla | Motivo |
|-------|--------|
| Siempre commitear el `N.json` generado | Es necesario para que `@AutoMigration` del siguiente salto funcione |
| Nunca añadir versiones pasadas a `fallbackToDestructiveMigrationFrom` | Una vez que una versión está en producción, borrar sus datos es inaceptable |
| Probar la migración en un dispositivo con la versión anterior instalada | El emulador permite instalar el APK viejo, luego el nuevo, y verificar que los datos sobreviven |
| `exportSchema = true` siempre | Con `false` no se generan los JSON y `@AutoMigration` no compila |

---

### Entidades actuales (v8)

| Tabla | Entidad | Campos clave |
|-------|---------|--------------|
| `food_entries` | `FoodEntryEntity` | date, name, kcal, protein, carbs, fat, timestamp |
| `food_items` | `FoodItemEntity` | name, itemType, source, servingMode, kcalPerServing, protein, carbs, fat |
| `daily_goals` | `DailyGoalEntity` | kcal, protein, carbs, fat (id fijo = 1) |
| `chat_messages` | `ChatMessageEntity` | date, text, isUser, timestamp, imagePath |
