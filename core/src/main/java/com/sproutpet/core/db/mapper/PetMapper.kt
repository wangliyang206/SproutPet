package com.sproutpet.core.db.mapper

import com.sproutpet.core.db.entity.PetEntity
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetAttributes
import com.sproutpet.core.domain.pet.PetGender
import com.sproutpet.core.domain.pet.PetGenes
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.Rarity

/**
 * Entity ↔ Domain 转换器。
 *
 * 存在的价值：把"数据库结构"与"业务模型"解耦。
 * 数据库改字段只影响本文件，业务层无感知。
 */
object PetMapper {

    /** Entity → Domain */
    fun toDomain(entity: PetEntity): Pet = Pet(
        id = entity.id,
        ownerId = entity.ownerId,
        nickname = entity.nickname,
        species = PetSpecies.fromId(entity.speciesId),
        rarity = Rarity.fromId(entity.rarity),
        stage = PetStage.fromId(entity.stage),
        level = entity.level,
        exp = entity.exp,
        attributes = PetAttributes(
            stamina = entity.stamina,
            strength = entity.strength,
            agility = entity.agility,
            wisdom = entity.wisdom,
            charm = entity.charm,
            intimacy = entity.intimacy
        ),
        genes = PetGenes(
            bodySize = entity.geneBodySize,
            hueShift = entity.geneHueShift,
            patternDensity = entity.genePatternDensity,
            eyeSize = entity.geneEyeSize,
            tailLength = entity.geneTailLength,
            liveliness = entity.geneLiveliness
        ),
        bornAt = entity.bornAt,
        hatchAt = entity.hatchAt,
        isHatched = entity.isHatched,
        spouseId = entity.spouseId,
        familyId = entity.familyId,
        parentIdA = entity.parentIdA,
        parentIdB = entity.parentIdB,
        batchNo = entity.batchNo,
        // ⚠️ 不映射 entity.isRetired：`Pet.isRetired` 现在是 **stage 的派生值**（单一真相源），
        // 列 `is_retired` 只是供 DAO 走索引过滤的缓存。见 PetEntity 该列的说明。
        exoticBlood = entity.exoticBlood,
        hasFullDeityForm = entity.hasFullDeityForm,
        gender = PetGender.fromId(entity.gender),
        generation = entity.generation,
        isMain = entity.isMain,
        homeSlot = entity.homeSlot,
        desktopSlot = entity.desktopSlot,
        fusionMarks = entity.fusionMarks
    )

    /**
     * Domain → Entity。
     *
     * @param generation 世代兜底值；**默认取 `pet.generation`（领域模型是真相源）**。
     *   显式传参仅用于"首次以父母世代落库后代"的场景（正常应先算好 `pet.generation`，
     *   见 [com.sproutpet.core.domain.pet.GrowthRules.GenerationRules.offspringGeneration]）。
     */
    fun toEntity(pet: Pet, generation: Int = pet.generation, existing: PetEntity? = null): PetEntity =
        PetEntity(
            id = pet.id,
            ownerId = pet.ownerId,
            nickname = pet.nickname,
            speciesId = pet.species.id,
            rarity = pet.rarity.id,
            stage = pet.stage.id,
            level = pet.level,
            exp = pet.exp,
            stamina = pet.attributes.stamina,
            strength = pet.attributes.strength,
            agility = pet.attributes.agility,
            wisdom = pet.attributes.wisdom,
            charm = pet.attributes.charm,
            intimacy = pet.attributes.intimacy,
            power = pet.attributes.power,
            geneBodySize = pet.genes.bodySize,
            geneHueShift = pet.genes.hueShift,
            genePatternDensity = pet.genes.patternDensity,
            geneEyeSize = pet.genes.eyeSize,
            geneTailLength = pet.genes.tailLength,
            geneLiveliness = pet.genes.liveliness,
            bornAt = pet.bornAt,
            hatchAt = pet.hatchAt,
            isHatched = pet.isHatched,
            spouseId = pet.spouseId,
            familyId = pet.familyId,
            parentIdA = pet.parentIdA,
            parentIdB = pet.parentIdB,
            generation = generation,
            batchNo = pet.batchNo,
            isRetired = pet.isRetired,
            exoticBlood = pet.exoticBlood,
            hasFullDeityForm = pet.hasFullDeityForm,
            gender = pet.gender.id,
            isMain = pet.isMain,
            homeSlot = pet.homeSlot,
            desktopSlot = pet.desktopSlot,
            fusionMarks = pet.fusionMarks,
            remoteId = existing?.remoteId,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )

    fun toDomainList(entities: List<PetEntity>): List<Pet> = entities.map(::toDomain)
}
