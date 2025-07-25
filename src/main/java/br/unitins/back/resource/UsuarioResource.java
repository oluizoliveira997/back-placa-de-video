package br.unitins.back.resource;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;

import br.unitins.back.dto.request.usuario.EnderecoDTO;
import br.unitins.back.dto.request.usuario.TelefoneDTO;
import br.unitins.back.dto.request.usuario.UsuarioDTO;
import br.unitins.back.dto.response.UsuarioResponseDTO;
import br.unitins.back.form.ImageForm;
import br.unitins.back.repository.UsuarioRepository;
import br.unitins.back.service.usuario.UsuarioFileService;
import br.unitins.back.service.usuario.UsuarioService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.Response.Status;

@Path("/usuarios")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UsuarioResource {

    private static final Logger LOGGER = Logger.getLogger(UsuarioResource.class.getName());

    @Inject
    UsuarioService service;

    @Inject
    UsuarioFileService fileService;

    @Inject
    UsuarioRepository usuarioRepository;

    @POST
    public Response insert(UsuarioDTO dto) {
        LOGGER.info("Iniciando inserção de novo usuário");
        Response response = Response.status(Status.CREATED).entity(service.insert(dto)).build();
        LOGGER.info("Usuário inserido com sucesso");
        return response;
    }

    @PUT
    @Transactional
    @Path("/{id}")
    public Response update(@Valid UsuarioDTO dto, @PathParam("id") Long id) {
        LOGGER.info("Iniciando atualização do usuário com ID: " + id);
        LOGGER.debug("Payload recebido: " + dto);
        try {
            UsuarioResponseDTO response = service.update(dto, id);
            LOGGER.info("Usuário com ID: " + id + " atualizado com sucesso");
            return Response.ok(response).build();
        } catch (ConstraintViolationException e) {
            LOGGER.error("Erro de validação: " + e.getMessage(), e);
            List<String> violations = e.getConstraintViolations().stream()
                    .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                    .collect(Collectors.toList());
            return Response.status(Status.BAD_REQUEST)
                    .entity(violations)
                    .build();
        } catch (IllegalArgumentException e) {
            LOGGER.error("Erro ao atualizar usuário: " + e.getMessage(), e);
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NotFoundException e) {
            LOGGER.error("Usuário não encontrado: " + e.getMessage(), e);
            return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            LOGGER.error("Erro interno ao atualizar usuário: " + e.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro interno no servidor: " + e.getMessage())
                    .build();
        }
    }

    @DELETE
    @Transactional
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        LOGGER.info("Iniciando remoção do usuário com ID: " + id);
        try {
            service.delete(id);
            LOGGER.info("Usuário com ID: " + id + " removido com sucesso");
            return Response.noContent().build();
        } catch (NotFoundException e) {
            LOGGER.error("Usuário não encontrado: " + e.getMessage());
            return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            LOGGER.error("Erro ao deletar usuário: " + e.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro ao deletar usuário: " + e.getMessage())
                    .build();
        }
    }

    @GET
    public List<UsuarioResponseDTO> findAll(
            @QueryParam("page") @DefaultValue("0") Integer page,
            @QueryParam("pageSize") @DefaultValue("8") Integer pageSize) {
        return service.findAll(page, pageSize);
    }

    @GET
    @Path("/{id}")
    public Response findById(@PathParam("id") Long id) {
        LOGGER.info("Buscando usuário com ID: " + id);
        Response response = Response.ok(service.findById(id)).build();
        LOGGER.info("Usuário com ID: " + id + " recuperado com sucesso");
        return response;
    }

    @GET
    @Path("/search")
    public Response findByNome(@QueryParam("nome") String nome) {
        LOGGER.info("Buscando usuário pelo nome: " + nome);
        Response response = Response.ok(service.findByNome(nome)).build();
        LOGGER.info("Usuários com nome: " + nome + " recuperados com sucesso");
        return response;
    }

    @GET
    @Path("/count")
    public long count() {
        return service.count();
    }

    @GET
    @Path("/exists")
    public Response checkExists(@QueryParam("login") String login, @QueryParam("email") String email, @QueryParam("cpf") String cpf) {
        if (login != null) {
            return Response.ok(usuarioRepository.findByLogin(login) != null).build();
        } else if (email != null) {
            return Response.ok(usuarioRepository.findByEmail(email) != null).build();
        } else if (cpf != null) {
            return Response.ok(usuarioRepository.findByCpf(cpf) != null).build();
        }
        return Response.status(Response.Status.BAD_REQUEST).build();
    }

    @PATCH
    @Path("/{id}/upload/imagem")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    public Response salvarImagemUsuario(@PathParam("id") Long id, @MultipartForm ImageForm form) {
        LOGGER.info("Iniciando atualização do nome da imagem do usuário com ID: " + id);
        try {
            String nomeImagem = fileService.salvar(form.getNomeImagem(), form.getImagem());
            UsuarioResponseDTO response = service.updateNomeImagem(id, nomeImagem);

            if (response == null) {
                LOGGER.warn("Usuário com ID: " + id + " não encontrado.");
                return Response.status(Status.NOT_FOUND).entity("Imagem não encontrada").build();
            }
            LOGGER.info("Nome da imagem atualizado com sucesso para o usuário com ID: " + id);
            return Response.ok(response).build();
        } catch (IOException e) {
            LOGGER.error("Erro ao salvar imagem: " + e.getMessage());
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/download/imagem/{nomeImagem}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@PathParam("nomeImagem") String nomeImagem) {
        LOGGER.info("Iniciando download da imagem: " + nomeImagem);
        File file = fileService.obter(nomeImagem);
        if (file == null || !file.exists()) {
            LOGGER.warn("Imagem: " + nomeImagem + " não encontrada");
            return Response.status(Status.NOT_FOUND).entity("Imagem não encontrada.").build();
        }
        ResponseBuilder response = Response.ok((Object) file);
        response.header("Content-Disposition", "attachment;filename=\"" + file.getName() + "\"");
        LOGGER.info("Download da imagem: " + nomeImagem + " realizado com sucesso");
        return response.build();

    }

    @POST
    @Transactional
    @Path("/{id}/telefones")
    public Response addTelefone(@PathParam("id") Long id, @Valid TelefoneDTO telefoneDTO) {
        LOGGER.infof("Adicionando telefone para usuário ID: %d", id);
        try {
            UsuarioResponseDTO response = service.addTelefone(id, telefoneDTO);
            LOGGER.infof("Telefone adicionado com sucesso para usuário ID: %d", id);
            return Response.ok(response).build();
        } catch (NotFoundException e) {
            LOGGER.error("Usuário não encontrado: " + e.getMessage(), e);
            return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (ConstraintViolationException e) {
            LOGGER.error("Erro de validação: " + e.getMessage(), e);
            List<String> violations = e.getConstraintViolations().stream()
                    .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                    .collect(Collectors.toList());
            return Response.status(Status.BAD_REQUEST).entity(violations).build();
        } catch (Exception e) {
            LOGGER.error("Erro ao adicionar telefone: " + e.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro interno no servidor: " + e.getMessage())
                    .build();
        }
    }

    @DELETE
    @Transactional
    @Path("/{id}/telefones/{telefoneId}")
    public Response removeTelefone(@PathParam("id") Long id, @PathParam("telefoneId") Long telefoneId) {
        LOGGER.infof("Removendo telefone ID: %d do usuário ID: %d", telefoneId, id);
        try {
            UsuarioResponseDTO response = service.removeTelefone(id, telefoneId);
            LOGGER.infof("Telefone ID: %d removido com sucesso", telefoneId);
            return Response.ok(response).build();
        } catch (NotFoundException e) {
            LOGGER.error("Usuário ou telefone não encontrado: " + e.getMessage(), e);
            return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            LOGGER.error("Erro ao remover telefone: " + e.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro interno no servidor: " + e.getMessage())
                    .build();
        }
    }

    @POST
    @Transactional
    @Path("/{id}/enderecos")
    public Response addEndereco(@PathParam("id") Long id, @Valid EnderecoDTO enderecoDTO) {
        LOGGER.infof("Adicionando endereço para usuário ID: %d", id);
        try {
            UsuarioResponseDTO response = service.addEndereco(id, enderecoDTO);
            LOGGER.infof("Endereço adicionado com sucesso para usuário ID: %d", id);
            return Response.ok(response).build();
        } catch (NotFoundException e) {
            LOGGER.error("Usuário não encontrado: " + e.getMessage(), e);
            return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (ConstraintViolationException e) {
            LOGGER.error("Erro de validação: " + e.getMessage(), e);
            List<String> violations = e.getConstraintViolations().stream()
                    .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                    .collect(Collectors.toList());
            return Response.status(Status.BAD_REQUEST).entity(violations).build();
        } catch (Exception e) {
            LOGGER.error("Erro ao adicionar endereço: " + e.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro interno no servidor: " + e.getMessage())
                    .build();
        }
    }

    @DELETE
    @Transactional
    @Path("/{id}/enderecos/{enderecoId}")
    public Response removeEndereco(@PathParam("id") Long id, @PathParam("enderecoId") Long enderecoId) {
        LOGGER.infof("Removendo endereço ID: %d do usuário ID: %d", enderecoId, id);
        try {
            UsuarioResponseDTO response = service.removeEndereco(id, enderecoId);
            LOGGER.infof("Endereço ID: %d removido com sucesso", enderecoId);
            return Response.ok(response).build();
        } catch (NotFoundException e) {
            LOGGER.error("Usuário ou endereço não encontrado: " + e.getMessage(), e);
            return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            LOGGER.error("Erro ao remover endereço: " + e.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR)
                    .entity("Erro interno no servidor: " + e.getMessage())
                    .build();
        }
    }

    @POST
    @Transactional
    @Path("/{id}/change-password")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response changePassword(@PathParam("id") Long id, Map<String, String> payload) {
        LOGGER.infof("Alterando senha para usuário ID: %d", id);
        String currentPassword = payload.get("currentPassword");
        String newPassword = payload.get("newPassword");
        if (currentPassword == null || newPassword == null) {
            return Response.status(Status.BAD_REQUEST).entity("Senha atual e nova são obrigatórias").build();
        }
        try {
            service.changePassword(id, currentPassword, newPassword);
            return Response.ok().build();
        } catch (NotFoundException e) {
            return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (Exception e) {
            LOGGER.error("Erro ao alterar senha: " + e.getMessage(), e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity("Erro interno no servidor").build();
        }
    }

    @POST
    @Path("/validar-senha")
    public Response validarSenha(Map<String, Object> payload) {
        try {
            Long id = Long.parseLong(payload.get("id").toString());
            String senha = payload.get("senha").toString();

            boolean valido = service.validarSenha(id, senha);
            return Response.ok(Map.of("valido", valido)).build();
        } catch (NotFoundException e) {
            return Response.status(Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            return Response.status(Status.BAD_REQUEST).entity("Requisição inválida").build();
        }
    }
}
