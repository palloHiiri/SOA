# Inventory Service

`inventory` is the master service for rolling-stock reference data, train-set compositions and train-set lifecycle.
The service uses Spring Boot MVC with PostgreSQL/JDBC and is built as a GraalVM Native Image.

## Responsibilities

The database owns:

- `carriage_types` — carriage models / series;
- `carriages` — physical carriage instances;
- `carriage_type_schemes` — versioned carriage layouts;
- `scheme_seat_positions` — seat coordinates;
- `train_sets` — reusable train-set compositions;
- `train_set_carriages` — carriage membership and order;
- lifecycle statuses and allowed lifecycle transitions;
- `train_set_cdc` — immutable snapshots published to downstream services when a train set enters `ACTIVE`.

The service intentionally does not expose CRUD for all these tables yet. The current API contains only train-set import and lifecycle management.

## API

OpenAPI:

```text
Docs/inventory_openapi.yaml
```

Entry point in the local Compose environment:

```text
http://localhost:4455
```

### Import a train set

```http
POST /api/v1/inventory/train-sets/import
```

The import document contains no database IDs and follows the same aggregate shape as the CDC snapshot. The main difference is that database IDs and lifecycle status are omitted. Repeated carriage types can use the local `ref` value to reuse the same newly-created type:

```json
{
  "code": "NORTH-01",
  "buildNumber": 3,
  "name": "North Express Set",
  "technicalName": "NORTH_EXPRESS_SET_003",
  "description": "Example",
  "carriages": [
    {
      "inventoryNumber": "INV-00101",
      "serialNumber": "SN-00101",
      "carriageNumber": "01",
      "position": 1,
      "carriageType": {
        "ref": "type-1",
        "code": "MODEL-61-4440",
        "name": "61-4440",
        "description": "Example carriage type",
        "scheme": {
          "code": "STANDARD",
          "name": "Standard scheme",
          "storageKey": "carriage-schemes/61-4440/v2.svg",
          "version": 2,
          "isActive": true,
          "seatPositions": [
            {
              "seatNumber": "1A",
              "x": 120.0,
              "y": 80.0,
              "rotation": 0.0
            }
          ]
        }
      }
    }
  ]
}
```

The PostgreSQL function `inventory_import_train_set(jsonb)` performs the entire import. It creates the train set, carriage types, schemes, seat positions, physical carriages and `train_set_carriages` rows in one database transaction. `scheme` mirrors the CDC snapshot shape; an optional `schemes` array may be used on the first occurrence of a carriage type when multiple scheme versions need to be imported.

The function intentionally does not write to `train_set_cdc`. A new train set is always created in `DRAFT`, and the existing lifecycle trigger creates a CDC snapshot only when the set later enters `ACTIVE`.

A uniqueness violation anywhere in the import aborts the complete transaction. The API exposes such data conflicts as `409 Conflict`. Invalid import structure is `400 Bad Request`.

### Change lifecycle

```http
POST /api/v1/inventory/train-sets/{trainSetId}/lifecycle
```

```json
{
  "status": "ACTIVE"
}
```

The database is the authoritative source of valid transitions. Current transitions are:

```text
DRAFT       -> ACTIVE
ACTIVE      -> MAINTENANCE
MAINTENANCE -> ACTIVE
ACTIVE      -> RETIRED
MAINTENANCE -> RETIRED
```

An invalid transition is returned as `400 Bad Request`.

When the transition enters `ACTIVE`, the existing PostgreSQL trigger builds an immutable aggregate snapshot and inserts it into `train_set_cdc`. Debezium then publishes the snapshot to `inventory.train_set_snapshots`.

## Security

Oathkeeper protects both endpoints with the authenticated `SESSION` cookie and checks:

```text
Service:inventory-service#access@Role:Admin#members
```

The generic Keto namespace already supports `Role`. The bootstrap script creates the `Admin` role relation for the inventory service. User-to-role administration is intentionally not implemented yet, so an administrator membership must be provisioned separately when role management is added.

`clients-service` continues to use the `User` role and is not changed by this rule.

## Database import design

The CDC snapshot function produces database IDs because downstream consumers need stable identifiers. The import contract is deliberately different: database IDs are omitted and objects are linked with local JSON references.

This keeps an import portable between environments and prevents clients from choosing database primary keys.

The import function is stored in:

```text
Database/inventory/v1/004-import-functions.sql
```

and included by:

```text
Database/inventory/changelog-master.xml
```

## CDC

The existing CDC pipeline remains unchanged:

```text
train_sets lifecycle -> ACTIVE
        |
        v
train_set_cdc
        |
        v
Debezium / Kafka Connect
        |
        v
Redpanda: inventory.train_set_snapshots
```

Only `train_set_cdc` is published. Downstream services receive one complete aggregate snapshot per active version rather than reconstructing the normalized tables themselves.
