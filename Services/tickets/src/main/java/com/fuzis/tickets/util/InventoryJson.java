package com.fuzis.tickets.util;

import com.fuzis.tickets.dto.CarriageResponse;
import com.fuzis.tickets.dto.CarriageTypeResponse;
import com.fuzis.tickets.dto.SchemeResponse;
import com.fuzis.tickets.dto.SeatResponse;
import com.fuzis.tickets.dto.TrainSetResponse;
import com.fuzis.tickets.exception.ApiException;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public final class InventoryJson {
    private InventoryJson() { }

    public static JsonObject object(String data) {
        try (JsonReader reader = Json.createReader(new StringReader(data))) {
            return reader.readObject();
        } catch (RuntimeException ex) {
            throw ApiException.internal("Stored Inventory snapshot contains invalid JSON");
        }
    }

    public static TrainSetResponse trainSet(JsonObject o, long version) {
        TrainSetResponse r = new TrainSetResponse();
        r.setId(requiredInt(o, "id"));
        r.setCode(requiredString(o, "code"));
        r.setName(requiredString(o, "name"));
        r.setBuildNumber(requiredInt(o, "buildNumber"));
        r.setTechnicalName(requiredString(o, "technicalName"));
        r.setDescription(string(o, "description"));
        r.setSnapshotVersion(version);
        return r;
    }

    public static List<JsonObject> carriages(JsonObject trainSet) {
        JsonArray a = trainSet.getJsonArray("carriages");
        if (a == null) return List.of();
        List<JsonObject> result = new ArrayList<>(a.size());
        for (JsonValue v : a) if (v.getValueType() == JsonValue.ValueType.OBJECT) result.add(v.asJsonObject());
        return result;
    }

    public static CarriageResponse carriage(JsonObject o) {
        CarriageResponse r = new CarriageResponse();
        r.setId(integer(o, "id"));
        r.setCarriageNumber(string(o, "carriageNumber"));
        r.setPosition(integer(o, "position"));
        r.setSerialNumber(string(o, "serialNumber"));
        r.setInventoryNumber(string(o, "inventoryNumber"));
        JsonObject ct = objectOrNull(o, "carriageType");
        if (ct != null) r.setCarriageType(carriageType(ct));
        JsonObject scheme = objectOrNull(o, "scheme");
        if (scheme != null) r.setScheme(scheme(scheme));
        return r;
    }

    public static CarriageTypeResponse carriageType(JsonObject o) {
        CarriageTypeResponse r = new CarriageTypeResponse();
        r.setId(integer(o, "id")); r.setCode(string(o, "code")); r.setName(string(o, "name")); r.setDescription(string(o, "description"));
        return r;
    }

    public static SchemeResponse scheme(JsonObject o) {
        SchemeResponse r = new SchemeResponse();
        r.setId(integer(o, "id")); r.setCode(string(o, "code")); r.setName(string(o, "name")); r.setStorageKey(string(o, "storageKey")); r.setVersion(integer(o, "version"));
        return r;
    }

    public static SeatResponse seat(JsonObject o) {
        SeatResponse r = new SeatResponse();
        r.setId(integer(o, "id")); r.setSeatNumber(string(o, "seatNumber")); r.setX(number(o, "x")); r.setY(number(o, "y")); r.setRotation(number(o, "rotation"));
        return r;
    }

    public static List<JsonObject> seats(JsonObject carriage) {
        JsonObject scheme = objectOrNull(carriage, "scheme");
        if (scheme == null) return List.of();
        JsonArray a = scheme.getJsonArray("seatPositions");
        if (a == null) return List.of();
        List<JsonObject> result = new ArrayList<>(a.size());
        for (JsonValue v : a) if (v.getValueType() == JsonValue.ValueType.OBJECT) result.add(v.asJsonObject());
        return result;
    }

    public static String string(JsonObject o, String key) { return o.containsKey(key) && !o.isNull(key) ? o.getString(key) : null; }
    private static String requiredString(JsonObject o, String key) {
        String value = string(o, key);
        if (value == null || value.isBlank()) throw ApiException.internal("Invalid Inventory snapshot: missing " + key);
        return value;
    }
    private static int requiredInt(JsonObject o, String key) {
        Integer value = integer(o,key); if (value == null) throw ApiException.internal("Invalid Inventory snapshot: missing " + key); return value;
    }
    public static Integer integer(JsonObject o, String key) {
        if (!o.containsKey(key) || o.isNull(key)) return null;
        return o.getJsonNumber(key).intValue();
    }
    public static double number(JsonObject o, String key) {
        if (!o.containsKey(key) || o.isNull(key)) return 0.0;
        return o.getJsonNumber(key).doubleValue();
    }
    public static JsonObject objectOrNull(JsonObject o, String key) {
        if (!o.containsKey(key) || o.isNull(key)) return null;
        return o.getJsonObject(key);
    }
}
