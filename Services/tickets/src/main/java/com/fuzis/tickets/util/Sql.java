package com.fuzis.tickets.util;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public final class Sql {
    private Sql() { }

    public static void bind(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            statement.setObject(i + 1, params.get(i));
        }
    }
}
