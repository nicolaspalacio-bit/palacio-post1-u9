package com.clinica.recordatorios.channel;

import com.clinica.recordatorios.domain.CanalNotificacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias de ReminderChannelFactoryImpl (Factory Method)")
class ReminderChannelFactoryImplTest {

    @Mock
    private EmailReminderChannel emailReminderChannel;

    @Mock
    private SmsReminderChannel smsReminderChannel;

    private ReminderChannelFactoryImpl factory;

    @BeforeEach
    void setUp() {
        factory = new ReminderChannelFactoryImpl(emailReminderChannel, smsReminderChannel);
    }

    @Test
    @DisplayName("Factory Method: resuelve la estrategia EmailReminderChannel para CanalNotificacion.EMAIL")
    void obtenerCanal_tipoEmail_retornaEstrategiaEmail() {
        ReminderChannel canal = factory.obtenerCanal(CanalNotificacion.EMAIL);
        assertSame(emailReminderChannel, canal, "Debe retornar la instancia concreta de EmailReminderChannel");
    }

    @Test
    @DisplayName("Factory Method: resuelve la estrategia SmsReminderChannel para CanalNotificacion.SMS")
    void obtenerCanal_tipoSms_retornaEstrategiaSms() {
        ReminderChannel canal = factory.obtenerCanal(CanalNotificacion.SMS);
        assertSame(smsReminderChannel, canal, "Debe retornar la instancia concreta de SmsReminderChannel");
    }
}
