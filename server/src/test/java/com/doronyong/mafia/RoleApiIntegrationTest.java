package com.doronyong.mafia;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.doronyong.mafia.domain.*;
import com.doronyong.mafia.dto.RoleApiDtos.SubmitActionRequest;
import com.doronyong.mafia.repository.*;
import com.doronyong.mafia.service.*;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
class RoleApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired RoomRepository rooms;
    @Autowired RoomPlayerRepository players;
    @Autowired RoomActionRepository actions;
    @Autowired RoomReportRepository reports;
    @Autowired RoleService roles;
    @Autowired NightResolutionService nights;
    Long roomsId;

    @BeforeEach void setUp() {
        reports.deleteAll();
        actions.deleteAll();
        players.deleteAll();
        rooms.deleteAll();
        roomsId = rooms.save(new RoomEntity(RoomPhase.NIGHT, 1)).getId();
    }

    void add(long id, String role, boolean alive) {
        players.save(new RoomPlayerEntity(roomsId, 100L + id, id, role, alive));
    }

    void submit(long actor, String code, long target) {
        roles.submitAction(roomsId, 100L + actor,
            new SubmitActionRequest(UUID.randomUUID(), code, target));
    }

    String reportText(long id) {
        return roles.getMyReports(roomsId, 100L + id).reports().toString();
    }

    @Test void catalogueContainsOnlyActiveRoles() throws Exception {
        String body = mvc.perform(get("/api/v1/roles"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("CREW_MONKEY"));
        assertTrue(body.contains("PIRATE_PARROT"));
        for (String excluded : new String[] {
            "PIRATE_KRAKEN", "PIRATE_SPY", "CREW_GHOST", "NEUTRAL_MONKEY"
        }) assertFalse(body.contains(excluded));
        mvc.perform(get("/api/v1/roles").param("faction", "NEUTRAL"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roles.length()").value(0));
    }

    @Test void privateRoleShowsPirateAlliesAndParrotAbilities() throws Exception {
        add(0, "PIRATE_RAIDER", true);
        add(1, "PIRATE_PARROT", true);
        add(2, "CREW_DOCTOR", true);
        mvc.perform(get("/api/v1/games/{gamesId}/me/role", roomsId))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/games/{gamesId}/me/role", roomsId)
                .with(jwt().jwt(token -> token.subject("100"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gamesId").value(roomsId))
            .andExpect(jsonPath("$.allies[0]").value(1))
            .andExpect(jsonPath("$.abilities[0].actionCode").value("TEAM_ATTACK_VOTE"));
        mvc.perform(get("/api/v1/games/{gamesId}/me/role", roomsId)
                .with(jwt().jwt(token -> token.subject("101"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.abilities.length()").value(2));
        mvc.perform(get("/api/v1/rooms/{roomsId}/me/role", roomsId)
                .with(jwt().jwt(token -> token.subject("100"))))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/games/{gamesId}/me/reports", roomsId)
                .with(jwt().jwt(token -> token.subject("999"))))
            .andExpect(status().isForbidden());
    }

    @Test void actionRetryReturnsSameResultAndNewRequestConflicts() throws Exception {
        add(0, "CREW_DOCTOR", true);
        add(1, "PIRATE_RAIDER", true);
        String body = """
            {"requestId":"%s","actionCode":"PROTECT","targetPlayerId":1}
            """.formatted(UUID.randomUUID());
        String first = mvc.perform(post("/api/v1/games/{gamesId}/actions", roomsId)
                .with(jwt().jwt(token -> token.subject("100")))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.gamesId").value(roomsId))
            .andReturn().getResponse().getContentAsString();
        String retry = mvc.perform(post("/api/v1/games/{gamesId}/actions", roomsId)
                .with(jwt().jwt(token -> token.subject("100")))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals(first, retry);
        assertEquals(1, actions.count());
        mvc.perform(post("/api/v1/games/{gamesId}/actions", roomsId)
                .with(jwt().jwt(token -> token.subject("100")))
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"requestId":"%s","actionCode":"PROTECT","targetPlayerId":0}
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isConflict());
    }

    @Test void captainLookoutParrotAndDoctorResolvePrivately() {
        add(0, "CREW_CAPTAIN", true);
        add(1, "CREW_LOOKOUT", true);
        add(2, "CREW_DOCTOR", true);
        add(3, "PIRATE_RAIDER", true);
        add(4, "PIRATE_PARROT", true);
        add(5, "CREW_SAILOR", true);
        submit(0, "INVESTIGATE_FACTION", 3);
        submit(1, "WATCH_VISITORS", 5);
        submit(2, "PROTECT", 5);
        submit(3, "TEAM_ATTACK_VOTE", 5);
        submit(4, "WATCH_ACTION", 2);
        submit(4, "TEAM_ATTACK_VOTE", 5);
        nights.resolveNight(roomsId);
        assertTrue(players.findByRoomIdAndPlayerId(roomsId, 5L).orElseThrow().isAlive());
        assertTrue(reportText(0).contains("PIRATE"));
        assertFalse(reportText(0).contains("VISITORS"));
        assertTrue(reportText(1).contains("2, 3"));
        assertTrue(reportText(4).contains("PROTECT"));
        assertEquals(RoomPhase.DAY, rooms.findById(roomsId).orElseThrow().getPhase());
    }

    @Test void boatswainBlocksProtectionThenPiratesWinAtParity() {
        add(0, "CREW_BOATSWAIN", true);
        add(1, "CREW_DOCTOR", true);
        add(2, "CREW_SAILOR", true);
        add(3, "PIRATE_RAIDER", true);
        add(4, "PIRATE_PARROT", true);
        submit(0, "BLOCK", 1);
        submit(1, "PROTECT", 2);
        submit(3, "TEAM_ATTACK_VOTE", 2);
        submit(4, "TEAM_ATTACK_VOTE", 2);
        nights.resolveNight(roomsId);
        assertFalse(players.findByRoomIdAndPlayerId(roomsId, 2L).orElseThrow().isAlive());
        assertTrue(reportText(0).contains("NIGHT_DEATH"));
        assertEquals(Faction.PIRATE, rooms.findById(roomsId).orElseThrow().getWinnerFaction());
    }

    @Test void oneOfTwoPirateVotesCannotKill() {
        add(0, "CREW_SAILOR", true);
        add(1, "CREW_DOCTOR", true);
        add(2, "CREW_CAPTAIN", true);
        add(3, "PIRATE_RAIDER", true);
        add(4, "PIRATE_PARROT", true);
        submit(3, "TEAM_ATTACK_VOTE", 0);
        nights.resolveNight(roomsId);
        assertTrue(players.findByRoomIdAndPlayerId(roomsId, 0L).orElseThrow().isAlive());
        assertFalse(reportText(0).contains("NIGHT_DEATH"));
    }

    @Test void gunnerShootsOnFirstDayAndWins() {
        roomsId = rooms.save(new RoomEntity(RoomPhase.DAY, 1)).getId();
        add(0, "CREW_GUNNER", true);
        add(1, "PIRATE_RAIDER", true);
        add(2, "CREW_SAILOR", true);
        submit(0, "DAY_SHOOT", 1);
        assertFalse(players.findByRoomIdAndPlayerId(roomsId, 1L).orElseThrow().isAlive());
        assertEquals(Faction.CREW, rooms.findById(roomsId).orElseThrow().getWinnerFaction());
        assertTrue(reportText(2).contains("DAY_SHOOT"));
        assertEquals(0, roles.getMyRole(roomsId, 100L).abilities().get(0).remainingUses());
    }

    @Test void doctorCannotProtectSelfOnConsecutiveNights() {
        add(0, "CREW_DOCTOR", true);
        add(1, "PIRATE_RAIDER", true);
        add(2, "CREW_SAILOR", true);
        submit(0, "PROTECT", 0);
        nights.resolveNight(roomsId);
        nights.beginNextNight(roomsId);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> submit(0, "PROTECT", 0));
        assertEquals(409, error.getStatusCode().value());
        submit(0, "PROTECT", 2);
    }

    @Test void drunkReadsTwoCorpsesAcrossNights() {
        add(0, "CREW_DRUNK", true);
        add(1, "CREW_SAILOR", false);
        add(2, "CREW_DOCTOR", false);
        add(3, "CREW_CAPTAIN", true);
        add(4, "PIRATE_RAIDER", true);
        submit(0, "READ_CORPSE_ROLE", 1);
        nights.resolveNight(roomsId);
        nights.beginNextNight(roomsId);
        submit(0, "READ_CORPSE_ROLE", 2);
        nights.resolveNight(roomsId);
        assertTrue(reportText(0).contains("CREW_SAILOR"));
        assertTrue(reportText(0).contains("CREW_DOCTOR"));
        assertEquals(0, roles.getMyRole(roomsId, 100L).abilities().get(0).remainingUses());
        assertFalse(reportText(3).contains("CORPSE_ROLE"));
    }

    @Test void monkeySeesCaptainDisguiseAndFalseInvestigation() {
        add(0, "CREW_MONKEY", true); // 0번은 선장으로 위장된다.
        add(1, "PIRATE_RAIDER", true);
        add(2, "CREW_SAILOR", true);
        add(3, "CREW_CAPTAIN", true);
        assertEquals("CREW_CAPTAIN", roles.getMyRole(roomsId, 100L).role().code());
        submit(0, "INVESTIGATE_FACTION", 1);
        submit(3, "INVESTIGATE_FACTION", 1);
        nights.resolveNight(roomsId);
        assertTrue(reportText(0).contains("CREW"));
        assertTrue(reportText(3).contains("PIRATE"));
    }
}
