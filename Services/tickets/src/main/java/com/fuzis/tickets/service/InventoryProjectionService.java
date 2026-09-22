package com.fuzis.tickets.service;

import com.fuzis.tickets.dto.CarriageResponse;
import com.fuzis.tickets.dto.PageResponse;
import com.fuzis.tickets.dto.SeatResponse;
import com.fuzis.tickets.dto.TrainSetResponse;
import com.fuzis.tickets.exception.ApiException;
import com.fuzis.tickets.repository.InventoryProjectionRepository;
import com.fuzis.tickets.repository.InventoryProjectionRepository.TrainSetSnapshot;
import com.fuzis.tickets.util.InventoryJson;
import com.fuzis.tickets.util.Pagination;
import com.fuzis.tickets.util.SortParser;
import com.fuzis.tickets.util.Sorts;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import java.sql.SQLException;
import java.util.List;

@Dependent
public class InventoryProjectionService {
    private final InventoryProjectionRepository repository;
    @Inject public InventoryProjectionService(InventoryProjectionRepository repository){this.repository=repository;}

    public PageResponse<TrainSetResponse> trainSets(int page,int size,List<String> sort,Integer id,String code,String name,Integer buildNumber,String technicalName){
        Pagination p=Pagination.of(page,size,maxSize());var sorts=SortParser.parse(sort,Sorts.TRAIN_SETS,"id");try{long total=repository.countTrainSets(id,code,name,buildNumber,technicalName);return new PageResponse<>(repository.findTrainSets(size,p.offset(),id,code,name,buildNumber,technicalName,sorts),page,size,total);}catch(SQLException e){throw ApiException.internal("Database operation failed");}}

    public TrainSetResponse trainSet(int id){if(id<1)throw ApiException.badRequest("trainSetId must be greater than or equal to 1");try{TrainSetSnapshot s=repository.latestTrainSet(id).orElseThrow(()->ApiException.notFound("Train set "+id+" not found"));return InventoryJson.trainSet(InventoryJson.object(s.data()),s.version());}catch(SQLException e){throw ApiException.internal("Database operation failed");}}

    public PageResponse<CarriageResponse> carriages(int trainSetId,int page,int size,List<String> sort,String carriageNumber,Integer id,Integer position,String inventoryNumber,String serialNumber,String carriageTypeCode){
        validateTrainSet(trainSetId);Pagination p=Pagination.of(page,size,maxSize());var sorts=SortParser.parse(sort,Sorts.CARRIAGES,"position");try{TrainSetSnapshot snapshot=repository.latestTrainSet(trainSetId).orElseThrow(()->ApiException.notFound("Train set "+trainSetId+" not found"));long total=repository.countCarriages(trainSetId,carriageNumber,id,position,inventoryNumber,serialNumber,carriageTypeCode);return new PageResponse<>(repository.findCarriages(trainSetId,size,p.offset(),carriageNumber,id,position,inventoryNumber,serialNumber,carriageTypeCode,sorts),page,size,total);}catch(SQLException e){throw ApiException.internal("Database operation failed");}}

    public PageResponse<SeatResponse> seats(int trainSetId,String carriageNumber,int page,int size,List<String> sort,String seatNumber,Double x,Double y,Double rotation){
        validateTrainSet(trainSetId);if(carriageNumber==null||carriageNumber.isBlank()||carriageNumber.length()>16)throw ApiException.badRequest("carriageNumber must contain 1 to 16 characters");Pagination p=Pagination.of(page,size,maxSize());var sorts=SortParser.parse(sort,Sorts.SEATS,"seatNumber");try{TrainSetSnapshot snapshot=repository.latestTrainSet(trainSetId).orElseThrow(()->ApiException.notFound("Train set "+trainSetId+" not found"));JsonObject trainSet=InventoryJson.object(snapshot.data());boolean carriageExists=InventoryJson.carriages(trainSet).stream().anyMatch(c->carriageNumber.equals(InventoryJson.string(c,"carriageNumber")));if(!carriageExists)throw ApiException.notFound("Carriage "+carriageNumber+" not found in train set "+trainSetId);long total=repository.countSeats(trainSetId,carriageNumber,seatNumber,x,y,rotation);return new PageResponse<>(repository.findSeats(trainSetId,carriageNumber,size,p.offset(),seatNumber,x,y,rotation,sorts),page,size,total);}catch(SQLException e){throw ApiException.internal("Database operation failed");}}

    private static void validateTrainSet(int id){if(id<1)throw ApiException.badRequest("trainSetId must be greater than or equal to 1");}
    private static int maxSize(){try{String v=System.getenv("TICKETS_MAX_PAGE_SIZE");return v==null||v.isBlank()?100:Integer.parseInt(v);}catch(Exception e){return 100;}}
}
