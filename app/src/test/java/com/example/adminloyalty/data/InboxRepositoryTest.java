package com.example.adminloyalty.data;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InboxRepositoryTest {

    @Test
    public void deliveryMessage_reportsSuccessfulReach() throws Exception {
        JSONObject response = new JSONObject()
                .put("reachableUsers", 12)
                .put("successCount", 15)
                .put("failureCount", 0);

        assertEquals("Push sent to 12 customer(s) on 15 device(s).",
                InboxRepository.deliveryMessage(response));
    }

    @Test
    public void deliveryMessage_reportsPartialFailures() throws Exception {
        JSONObject response = new JSONObject()
                .put("reachableUsers", 12)
                .put("successCount", 13)
                .put("failureCount", 2);

        assertEquals("Sent to 13 device(s) for 12 customer(s); 2 delivery failure(s).",
                InboxRepository.deliveryMessage(response));
    }
}
