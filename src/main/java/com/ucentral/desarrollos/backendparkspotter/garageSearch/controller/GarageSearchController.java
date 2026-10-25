package com.ucentral.desarrollos.backendparkspotter.garageSearch.controller;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageAvailabilityResponse;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageDetailResponse;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageMapResponse;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageSearchCriteria;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageSummaryResponse;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.MapBoundsRequest;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.realtime.GarageAvailabilityStreamService;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.service.GarageSearchService;
import com.ucentral.desarrollos.backendparkspotter.shared.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Endpoints públicos de búsqueda, mapa, detalle y estado en tiempo real de los garajes.
 */
@RestController
@RequestMapping("/api/v1/garages")
@RequiredArgsConstructor
public class GarageSearchController {

    private final GarageSearchService garageSearchService;
    private final GarageAvailabilityStreamService streamService;

    /** Búsqueda paginada con filtros. Ej: /search?q=centro&onlyAvailable=true&lat=4.71&lng=-74.07&radiusKm=3 */
    @GetMapping("/search")
    public ResponseEntity<PageResponse<GarageSummaryResponse>> search(@Valid @ModelAttribute GarageSearchCriteria criteria) {
        return ResponseEntity.ok(garageSearchService.search(criteria));
    }

    /** Marcadores del área visible del mapa. Acepta los mismos filtros que /search. */
    @GetMapping("/map")
    public ResponseEntity<GarageMapResponse> map(@Valid @ModelAttribute MapBoundsRequest bounds,
                                                 @Valid @ModelAttribute GarageSearchCriteria filters) {
        return ResponseEntity.ok(garageSearchService.mapMarkers(bounds, filters));
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<GarageDetailResponse> detail(@PathVariable UUID id,
                                                       @RequestParam(defaultValue = "false") boolean includeSpots) {
        return ResponseEntity.ok(garageSearchService.detail(id, includeSpots));
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<GarageAvailabilityResponse> availability(@PathVariable UUID id) {
        return ResponseEntity.ok(garageSearchService.availability(id));
    }

    /** Stream SSE de un garaje: primer evento con el estado actual y luego cada cambio. */
    @GetMapping(path = "/{id}/availability/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter availabilityStream(@PathVariable UUID id) {
        return streamService.subscribe(id, garageSearchService.availability(id));
    }

    /** Stream SSE con los cambios de todos los garajes (para el mapa). */
    @GetMapping(path = "/availability/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter allAvailabilityStream() {
        return streamService.subscribeAll();
    }
}
