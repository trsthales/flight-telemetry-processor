# flight-telemetry-processor

Protótipo de arquitetura para ingestão e processamento de telemetria aeronáutica em tempo real, inspirado no tráfego de mensagens ACARS entre aeronave e solo.

## O problema

Pacotes brutos de telemetria costumam trazer dezenas de parâmetros secundários e logs de diagnóstico que não precisam ser consultados imediatamente. Em horários de pico da malha aérea, fazer o parse completo e gravar tudo de forma síncrona no banco relacional gera gargalo de I/O e atrasa a atualização do painel da engenharia em solo.

Quem acompanha a operação precisa visualizar meia dúzia de indicadores vitais (alertas de motor, combustível, pressurização) quase instantaneamente, enquanto o pacote completo serve para auditoria e análise histórica posterior.

## Arquitetura

O fluxo separa a leitura crítica da carga pesada de escrita:

1. **Fast-path (Estado atual):** A API recebe o pacote, barra duplicidades, extrai apenas os dados vitais e atualiza o estado da aeronave no Redis. O painel de monitoramento lê exclusivamente desse cache.
2. **Slow-path (Histórico):** No mesmo recebimento, o payload bruto vai para um tópico no Kafka particionado pelo prefixo da aeronave (preservando a ordem cronológica do voo). Um consumidor em background processa o pacote completo e persiste no PostgreSQL com confirmação manual de leitura, enviando pacotes corrompidos para uma DLQ após esgotar as tentativas.

## Stack

- Java 21 (Virtual Threads) + Spring Boot 3
- Redis 7 (cache de estado quente e chave de idempotência)
- Apache Kafka em modo KRaft
- PostgreSQL 16 + Flyway
- Testcontainers + JUnit 5

## Executando localmente

Suba os containers de apoio (Postgres, Redis e Kafka):

```bash
docker compose up -d
```

Inicie a aplicação:

```bash
./mvnw spring-boot:run
```