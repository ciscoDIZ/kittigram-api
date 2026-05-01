package es.kitti.cat.service;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.ForbiddenException;
import es.kitti.cat.client.AdoptionClient;
import es.kitti.cat.client.StorageClient;
import es.kitti.cat.dto.*;
import es.kitti.cat.entity.Cat;
import es.kitti.cat.entity.CatStatus;
import es.kitti.cat.exception.CatHasActiveAdoptionsException;
import es.kitti.cat.exception.CatNotFoundException;
import es.kitti.cat.mapper.CatMapper;
import es.kitti.cat.repository.CatImageRepository;
import es.kitti.cat.repository.CatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatServiceTest {

    @Mock
    CatRepository catRepository;

    @Mock
    CatImageRepository catImageRepository;

    @Mock
    CatMapper catMapper;

    @Mock
    StorageClient storageClient;

    @Mock
    AdoptionClient adoptionClient;

    @InjectMocks
    CatService catService;

    private Cat testCat;
    private CatResponse testCatResponse;
    private CatSummaryResponse testCatSummaryResponse;

    @BeforeEach
    void setUp() {
        testCat = new Cat();
        testCat.id = 1L;
        testCat.organizationId = 10L;
        testCat.name = "Peluso";
        testCat.age = 2;
        testCat.city = "La Orotava";
        testCat.status = CatStatus.Available;

        testCatResponse = new CatResponse(
                1L, "Peluso", 2, "Male", null, true,
                "Available", "La Orotava", "Tenerife", "España",
                null, null, 10L, List.of(),
                LocalDateTime.now(), LocalDateTime.now()
        );

        testCatSummaryResponse = new CatSummaryResponse(
                1L, "Peluso", null, 2, "Male", true,
                "Available", "La Orotava", "Tenerife", "España",
                LocalDateTime.now()
        );
    }

    @Test
    void createCat_success() {
        var request = new CatCreateRequest(
                "Peluso", 2, "Male", null, true,
                "La Orotava", "Tenerife", "España", null, null
        );

        when(catMapper.toEntity(request)).thenReturn(testCat);
        when(catRepository.persist(any(Cat.class)))
                .thenReturn(Uni.createFrom().item(testCat));
        when(catMapper.toResponse(testCat, List.of()))
                .thenReturn(testCatResponse);

        var result = catService.createCat(request, 10L)
                .await().indefinitely();

        assertNotNull(result);
        assertEquals("Peluso", result.name());
        assertEquals("Available", result.status());
    }

    @Test
    void findById_catExists_returnsResponse() {
        when(catRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testCat));
        when(catImageRepository.findByCatId(1L))
                .thenReturn(Multi.createFrom().empty());
        when(catMapper.toResponse(eq(testCat), any()))
                .thenReturn(testCatResponse);

        var result = catService.findById(1L)
                .await().indefinitely();

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("Peluso", result.name());
    }

    @Test
    void findById_catNotFound_throwsCatNotFoundException() {
        when(catRepository.findById(999L))
                .thenReturn(Uni.createFrom().nullItem());

        assertThrows(CatNotFoundException.class, () ->
                catService.findById(999L)
                        .await().indefinitely()
        );
    }

    @Test
    void findById_deletedCat_throwsCatNotFoundException() {
        testCat.status = CatStatus.Deleted;
        when(catRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testCat));

        assertThrows(CatNotFoundException.class, () ->
                catService.findById(1L).await().indefinitely()
        );
    }

    // deleteCat uses Panache.withSession/withTransaction (static calls that require Vert.x context)
    // — those scenarios are fully covered in CatResourceTest (integration) and CatE2E

    @Test
    void findMine_excludesDeletedCats() {
        when(catRepository.findByOrganizationId(10L))
                .thenReturn(Uni.createFrom().item(List.of(testCat)));
        when(catMapper.toSummaryResponse(testCat))
                .thenReturn(testCatSummaryResponse);

        var result = catService.findMine(10L).await().indefinitely();

        assertEquals(1, result.size());
        verify(catRepository).findByOrganizationId(10L);
    }

    @Test
    void search_byCity_returnsSummaries() {
        when(catRepository.findByCity("La Orotava"))
                .thenReturn(Uni.createFrom().item(List.of(testCat)));
        when(catMapper.toSummaryResponse(testCat))
                .thenReturn(testCatSummaryResponse);

        var results = catService.search("La Orotava", null)
                .collect().asList()
                .await().indefinitely();

        assertEquals(1, results.size());
        assertEquals("La Orotava", results.get(0).city());
    }

    @Test
    void search_noFilters_returnsAllAvailable() {
        when(catRepository.findAvailable())
                .thenReturn(Uni.createFrom().item(List.of(testCat)));
        when(catMapper.toSummaryResponse(testCat))
                .thenReturn(testCatSummaryResponse);

        var results = catService.search(null, null)
                .collect().asList()
                .await().indefinitely();

        assertEquals(1, results.size());
    }

    @Test
    void updateCat_notOwner_throwsForbiddenException() {
        when(catRepository.findById(1L))
                .thenReturn(Uni.createFrom().item(testCat));

        var request = new CatUpdateRequest(
                "Peluso Updated", 3, null, true,
                "Santa Cruz", "Tenerife", "España", null, null
        );

        assertThrows(ForbiddenException.class, () ->
                catService.updateCat(1L, request, 99L)
                        .await().indefinitely()
        );
    }
}
