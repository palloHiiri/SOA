package com.fuzis.tickets.util;

import java.util.Map;

public final class Sorts {
    private Sorts() { }
    public static final Map<String,String> TICKETS = SortParser.map(
            "id","t.id", "name","t.name", "creationDate","t.creation_date", "venueId","t.venue_id",
            "trainSetId","v.train_set_id", "carriageNumber","t.carriage_number", "seatNumber","t.seat_number",
            "refundable","t.refundable", "type","t.type", "basePrice","ph.base_price", "discount","ph.discount");
    public static final Map<String,String> VENUES = SortParser.map("id","id","name","name","trainSetId","train_set_id");
    public static final Map<String,String> PRICES = SortParser.map("id","id","ticketId","ticket_id","changedDate","changed_date","basePrice","base_price","discount","discount");
    public static final Map<String,String> TRAIN_SETS = SortParser.map("id","train_set_id","code","data->>'code'","name","data->>'name'","buildNumber","(data->>'buildNumber')::int","technicalName","data->>'technicalName'","snapshotVersion","version");
    public static final Map<String,String> CARRIAGES = SortParser.map("id","(c->>'id')::int","carriageNumber","c->>'carriageNumber'","position","(c->>'position')::int","inventoryNumber","c->>'inventoryNumber'","serialNumber","c->>'serialNumber'");
    public static final Map<String,String> SEATS = SortParser.map("id","(s->>'id')::int","seatNumber","s->>'seatNumber'","x","(s->>'x')::double precision","y","(s->>'y')::double precision","rotation","(s->>'rotation')::double precision");
}
