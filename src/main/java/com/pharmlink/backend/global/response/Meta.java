@Getter
@Builder
public class Meta {
    private String requestId;
    private String timestamp;

    public static Meta now() {
        return Meta.builder()
                .requestId(UUID.randomUUID().toString())
                .timestamp(Instant.now().toString())
                .build();
    }
}