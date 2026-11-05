package br.com.keyfalls.identidade.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(schema = "identidade", name = "usuarios")
public class CandidatoJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "cpf", nullable = false, unique = true, length = 11)
    private String cpf;

    @Column(name = "nome_completo", nullable = false, length = 180)
    private String nomeCompleto;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 255)
    private String senhaHash;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "logradouro", nullable = false, length = 200)
    private String logradouro;

    @Column(name = "numero", nullable = false, length = 30)
    private String numero;

    @Column(name = "bairro", nullable = false, length = 120)
    private String bairro;

    @Column(name = "cidade", nullable = false, length = 120)
    private String cidade;

    @Column(name = "uf", nullable = false, length = 2)
    private String uf;

    @Column(name = "cep", nullable = false, length = 8)
    private String cep;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        schema = "identidade",
        name = "usuario_roles",
        joinColumns = @JoinColumn(name = "usuario_id")
    )
    @Column(name = "role", nullable = false, length = 40)
    private Set<String> roles = new HashSet<>();

    protected CandidatoJpaEntity() {}

    public CandidatoJpaEntity(
        UUID id,
        String cpf,
        String nomeCompleto,
        String email,
        String senhaHash,
        boolean ativo,
        String logradouro,
        String numero,
        String bairro,
        String cidade,
        String uf,
        String cep,
        Double latitude,
        Double longitude,
        Instant criadoEm,
        Set<String> roles
    ) {
        this.id = id;
        this.cpf = cpf;
        this.nomeCompleto = nomeCompleto;
        this.email = email;
        this.senhaHash = senhaHash;
        this.ativo = ativo;
        this.logradouro = logradouro;
        this.numero = numero;
        this.bairro = bairro;
        this.cidade = cidade;
        this.uf = uf;
        this.cep = cep;
        this.latitude = latitude;
        this.longitude = longitude;
        this.criadoEm = criadoEm;
        this.roles = new HashSet<>(roles);
    }

    public UUID getId() { return id; }
    public String getCpf() { return cpf; }
    public String getNomeCompleto() { return nomeCompleto; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public boolean isAtivo() { return ativo; }
    public String getLogradouro() { return logradouro; }
    public String getNumero() { return numero; }
    public String getBairro() { return bairro; }
    public String getCidade() { return cidade; }
    public String getUf() { return uf; }
    public String getCep() { return cep; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Instant getCriadoEm() { return criadoEm; }
    public Set<String> getRoles() { return roles; }
}
