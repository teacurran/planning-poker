package com.terrencecurran.planningpoker.resource;

import com.terrencecurran.planningpoker.dto.RoomState;
import com.terrencecurran.planningpoker.entity.Room;
import com.terrencecurran.planningpoker.service.RoomService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RoomResource {
    
    @Inject
    RoomService roomService;
    
    @POST
    public Uni<Response> createRoom(CreateRoomRequest request) {
        return roomService.createRoom(request.name)
            .onItem().transform(room -> 
                Response.ok(new CreateRoomResponse(room.id)).build()
            );
    }
    
    @GET
    @Path("/{roomId}")
    public Uni<Response> getRoom(@PathParam("roomId") String roomId) {
        return roomService.getRoom(roomId)
            .onItem().ifNotNull().transform(room -> Response.ok(room).build())
            .onItem().ifNull().continueWith(Response.status(Response.Status.NOT_FOUND).build());
    }
    
    @GET
    @Path("/{roomId}/state")
    public Uni<RoomState> getRoomState(@PathParam("roomId") String roomId) {
        return roomService.getRoomState(roomId);
    }
    
    public static class CreateRoomRequest {
        public String name;
    }
    
    public static class CreateRoomResponse {
        public String roomId;
        
        public CreateRoomResponse() {}
        
        public CreateRoomResponse(String roomId) {
            this.roomId = roomId;
        }
    }
}