package com.doronyong.mafia.repository;

import com.doronyong.mafia.domain.Faction;
import com.doronyong.mafia.domain.RoleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<RoleEntity, String> {
    List<RoleEntity> findByEnabledTrueOrderByCodeAsc();
    List<RoleEntity> findByFactionAndEnabledTrueOrderByCodeAsc(Faction faction);
}
